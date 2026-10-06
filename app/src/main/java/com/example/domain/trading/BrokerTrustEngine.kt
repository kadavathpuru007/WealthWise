package com.example.domain.trading

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Broker Trust & Security Engine.
 *
 * Responsibilities:
 * 1. Safe local device package query using narrow, targeted PackageManager checks (no broad query permissions).
 * 2. Strict separation between local app detection and authoritative regulatory verification.
 * 3. Checking against versioned SEBI registrations and configurable RBI Alert List datasets.
 * 4. Grounded reporting: absence of an alert is NEVER labeled "Safe", as RBI alerts are not exhaustive.
 */
class BrokerTrustEngine(
    private val context: Context,
    private val regulatoryRepository: RegulatoryRepository = RegulatoryRepository(),
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Checks installed apps safely against known broker signatures and the RBI alert list.
     * Respects Android package visibility without requiring SMS, contacts, camera, or excessive permissions.
     */
    fun evaluateDeviceBrokerSecurity(): BrokerSecurityState {
        val pm = context.packageManager

        // 1. Check for entities flagged on the RBI Alert List first
        val alertList = regulatoryRepository.findAlertByPackage("") // Check registry
        val rbiAlerts = (regulatoryRepository as? RegulatoryRepository)?.getDatasetInfo()
        for (alert in (LocalRegulatoryDataSource()).getRbiAlertList()) {
            for (pkg in alert.packageHints) {
                if (isPackageInstalled(pm, pkg)) {
                    return BrokerSecurityState.Alert(
                        platformName = alert.entityName,
                        source = alert.source,
                        lastUpdated = alert.dateListed
                    )
                }
            }
        }

        // 2. Check for supported broker packages
        val knownBrokers = regulatoryRepository.getKnownBrokers()
        for (broker in knownBrokers) {
            for (pkg in broker.packageNames) {
                if (isPackageInstalled(pm, pkg)) {
                    // Broker is detected on device. Now verify regulatory status independently.
                    val verifiedEntry = regulatoryRepository.findBrokerByPackage(pkg)
                    return if (verifiedEntry != null && !verifiedEntry.registrationNumber.isNullOrBlank()) {
                        BrokerSecurityState.Verified(
                            brokerName = verifiedEntry.brokerName,
                            lastVerified = verifiedEntry.verifiedDate
                        )
                    } else {
                        // App is detected, but regulatory registration could not be verified automatically
                        BrokerSecurityState.DetectedUnverified(
                            brokerName = broker.brokerName
                        )
                    }
                }
            }
        }

        // 3. No supported broker app detected on device
        return BrokerSecurityState.Neutral
    }

    /**
     * Evaluates security status for a specific broker name (e.g. from user trade journal or transaction merchant).
     */
    fun evaluateBrokerByName(brokerName: String): BrokerSecurityState {
        if (brokerName.isBlank()) return BrokerSecurityState.Neutral

        // Check RBI Alert List
        val alertMatch = regulatoryRepository.findAlertByName(brokerName)
        if (alertMatch != null) {
            return BrokerSecurityState.Alert(
                platformName = alertMatch.entityName,
                source = alertMatch.source,
                lastUpdated = alertMatch.dateListed
            )
        }

        // Check SEBI registry
        val brokerMatch = regulatoryRepository.findBrokerByName(brokerName)
        if (brokerMatch != null && !brokerMatch.registrationNumber.isNullOrBlank()) {
            return BrokerSecurityState.Verified(
                brokerName = brokerMatch.brokerName,
                lastVerified = brokerMatch.verifiedDate
            )
        }

        return BrokerSecurityState.DetectedUnverified(brokerName = brokerName.trim())
    }

    /**
     * Reactive flow emitting the current broker trust state.
     */
    val brokerSecurityFlow: Flow<BrokerSecurityState> = flow {
        emit(BrokerSecurityState.Loading)
        val state = evaluateDeviceBrokerSecurity()
        emit(state)
    }.flowOn(ioDispatcher)

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Throwable) {
            false
        }
    }
}
