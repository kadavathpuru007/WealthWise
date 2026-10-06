package com.example.domain.trading

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Authoritative record of an entity listed on the RBI Alert List of unauthorized forex/trading platforms.
 */
data class RbiAlertEntry(
    val entityName: String,
    val aliases: List<String>,
    val packageHints: Set<String> = emptySet(),
    val source: String = "RBI Alert List",
    val dateListed: Long
)

/**
 * Versioned regulatory dataset metadata.
 */
data class RegulatoryDatasetInfo(
    val version: String,
    val lastUpdatedTimestamp: Long,
    val sourceDescription: String
) {
    val formattedLastUpdated: String
        get() {
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
            return sdf.format(Date(lastUpdatedTimestamp))
        }
}

/**
 * Interface for regulatory data access, enabling offline versioned datasets or dynamic remote updates.
 */
interface RegulatoryDataSource {
    fun getDatasetInfo(): RegulatoryDatasetInfo
    fun getRegisteredBrokers(): List<BrokerRegistryEntry>
    fun getRbiAlertList(): List<RbiAlertEntry>
    fun findBrokerByPackage(packageName: String): BrokerRegistryEntry?
    fun findBrokerByName(name: String): BrokerRegistryEntry?
    fun findAlertByPackage(packageName: String): RbiAlertEntry?
    fun findAlertByName(name: String): RbiAlertEntry?
}

/**
 * Versioned local regulatory dataset based on published SEBI registrations and RBI Alert List updates.
 * Can be replaced or updated via remote config or API without altering presentation logic.
 */
class LocalRegulatoryDataSource : RegulatoryDataSource {

    // Versioned dataset timestamp: e.g. Oct 2026
    private val datasetInfo = RegulatoryDatasetInfo(
        version = "2026.10.1",
        lastUpdatedTimestamp = 1790900000000L, // Oct 2026
        sourceDescription = "SEBI Registered Intermediaries & RBI Alert List Public Gazette"
    )

    // Authoritative known Indian brokers registered with SEBI
    private val registeredBrokers = listOf(
        BrokerRegistryEntry(
            brokerName = "Zerodha",
            packageNames = setOf("com.zerodha.kite3", "com.zerodha.coin"),
            officialName = "Zerodha Broking Limited",
            regulatoryCategory = RegulatoryCategory.STOCK_BROKER,
            registrationNumber = "INZ000031633",
            verifiedDate = 1790900000000L
        ),
        BrokerRegistryEntry(
            brokerName = "Groww",
            packageNames = setOf("com.nextbillion.groww"),
            officialName = "Nextbillion Technology Private Limited",
            regulatoryCategory = RegulatoryCategory.STOCK_BROKER,
            registrationNumber = "INZ000301838",
            verifiedDate = 1790900000000L
        ),
        BrokerRegistryEntry(
            brokerName = "Angel One",
            packageNames = setOf("com.msf.angelmobile"),
            officialName = "Angel One Limited",
            regulatoryCategory = RegulatoryCategory.STOCK_BROKER,
            registrationNumber = "INZ000161534",
            verifiedDate = 1790900000000L
        ),
        BrokerRegistryEntry(
            brokerName = "Upstox",
            packageNames = setOf("in.upstox.app"),
            officialName = "RKSV Securities India Private Limited",
            regulatoryCategory = RegulatoryCategory.STOCK_BROKER,
            registrationNumber = "INZ000185137",
            verifiedDate = 1790900000000L
        ),
        BrokerRegistryEntry(
            brokerName = "Dhan",
            packageNames = setOf("co.dhan"),
            officialName = "Moneylicious Securities Private Limited",
            regulatoryCategory = RegulatoryCategory.STOCK_BROKER,
            registrationNumber = "INZ000006031",
            verifiedDate = 1790900000000L
        ),
        BrokerRegistryEntry(
            brokerName = "INDmoney",
            packageNames = setOf("in.indwealth"),
            officialName = "INDstocks Private Limited",
            regulatoryCategory = RegulatoryCategory.STOCK_BROKER,
            registrationNumber = "INZ000305337",
            verifiedDate = 1790900000000L
        ),
        BrokerRegistryEntry(
            brokerName = "5Paisa",
            packageNames = setOf("com.fivepaisa.trade"),
            officialName = "5paisa Capital Limited",
            regulatoryCategory = RegulatoryCategory.STOCK_BROKER,
            registrationNumber = "INZ000010231",
            verifiedDate = 1790900000000L
        ),
        BrokerRegistryEntry(
            brokerName = "Paytm Money",
            packageNames = setOf("com.paytmmoney"),
            officialName = "Paytm Money Limited",
            regulatoryCategory = RegulatoryCategory.STOCK_BROKER,
            registrationNumber = "INZ000240532",
            verifiedDate = 1790900000000L
        ),
        BrokerRegistryEntry(
            brokerName = "ICICI Direct",
            packageNames = setOf("com.icicidirect"),
            officialName = "ICICI Securities Limited",
            regulatoryCategory = RegulatoryCategory.STOCK_BROKER,
            registrationNumber = "INZ000183631",
            verifiedDate = 1790900000000L
        )
    )

    // Configurable RBI Alert List data source: entities flagged by Reserve Bank of India
    private val rbiAlertList = listOf(
        RbiAlertEntry(
            entityName = "OctaFX",
            aliases = listOf("Octa", "Octa FX Trading"),
            packageHints = setOf("com.octafx", "com.octafx.trade"),
            source = "RBI Alert List of Unauthorized Forex/Trading Platforms",
            dateListed = 1790900000000L
        ),
        RbiAlertEntry(
            entityName = "Olymp Trade",
            aliases = listOf("OlympTrade", "Olymp"),
            packageHints = setOf("com.olymptrade"),
            source = "RBI Alert List of Unauthorized Forex/Trading Platforms",
            dateListed = 1790900000000L
        ),
        RbiAlertEntry(
            entityName = "Binomo",
            aliases = listOf("Binomo Invest"),
            packageHints = setOf("com.binomo"),
            source = "RBI Alert List of Unauthorized Forex/Trading Platforms",
            dateListed = 1790900000000L
        ),
        RbiAlertEntry(
            entityName = "IQ Option",
            aliases = listOf("IQOption"),
            packageHints = setOf("com.iqoption"),
            source = "RBI Alert List of Unauthorized Forex/Trading Platforms",
            dateListed = 1790900000000L
        ),
        RbiAlertEntry(
            entityName = "Expert Option",
            aliases = listOf("ExpertOption"),
            packageHints = setOf("com.expertoption"),
            source = "RBI Alert List of Unauthorized Forex/Trading Platforms",
            dateListed = 1790900000000L
        )
    )

    override fun getDatasetInfo(): RegulatoryDatasetInfo = datasetInfo

    override fun getRegisteredBrokers(): List<BrokerRegistryEntry> = registeredBrokers

    override fun getRbiAlertList(): List<RbiAlertEntry> = rbiAlertList

    override fun findBrokerByPackage(packageName: String): BrokerRegistryEntry? {
        val norm = packageName.trim().lowercase(Locale.ROOT)
        return registeredBrokers.firstOrNull { entry ->
            entry.packageNames.any { it.lowercase(Locale.ROOT) == norm }
        }
    }

    override fun findBrokerByName(name: String): BrokerRegistryEntry? {
        val norm = name.trim().lowercase(Locale.ROOT)
        if (norm.isEmpty()) return null
        return registeredBrokers.firstOrNull { entry ->
            entry.brokerName.lowercase(Locale.ROOT) == norm ||
                    entry.officialName.lowercase(Locale.ROOT) == norm ||
                    norm.contains(entry.brokerName.lowercase(Locale.ROOT))
        }
    }

    override fun findAlertByPackage(packageName: String): RbiAlertEntry? {
        val norm = packageName.trim().lowercase(Locale.ROOT)
        return rbiAlertList.firstOrNull { alert ->
            alert.packageHints.any { it.lowercase(Locale.ROOT) == norm }
        }
    }

    override fun findAlertByName(name: String): RbiAlertEntry? {
        val norm = name.trim().lowercase(Locale.ROOT)
        if (norm.isEmpty()) return null
        return rbiAlertList.firstOrNull { alert ->
            alert.entityName.lowercase(Locale.ROOT) == norm ||
                    alert.aliases.any { it.lowercase(Locale.ROOT) == norm || norm.contains(it.lowercase(Locale.ROOT)) }
        }
    }
}

/**
 * Regulatory repository coordinating datasets.
 */
class RegulatoryRepository(
    private val dataSource: RegulatoryDataSource = LocalRegulatoryDataSource()
) {
    fun getDatasetInfo(): RegulatoryDatasetInfo = dataSource.getDatasetInfo()

    fun findBrokerByPackage(packageName: String): BrokerRegistryEntry? =
        dataSource.findBrokerByPackage(packageName)

    fun findBrokerByName(name: String): BrokerRegistryEntry? =
        dataSource.findBrokerByName(name)

    fun findAlertByPackage(packageName: String): RbiAlertEntry? =
        dataSource.findAlertByPackage(packageName)

    fun findAlertByName(name: String): RbiAlertEntry? =
        dataSource.findAlertByName(name)

    fun getKnownBrokers(): List<BrokerRegistryEntry> = dataSource.getRegisteredBrokers()
}
