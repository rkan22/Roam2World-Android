package im.angry.openeuicc.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobilePackageDataTest {

    @Test
    fun priceForResellerUsesResellerPrice() {
        val pkg = pkg(resellerPrice = "80.00", dealerPrice = "70.00", basePrice = "100.00")
        assertEquals("80.00", pkg.priceFor("reseller"))
    }

    @Test
    fun priceForDealerUsesDealerPrice() {
        val pkg = pkg(resellerPrice = "80.00", dealerPrice = "70.00", basePrice = "100.00")
        assertEquals("70.00", pkg.priceFor("dealer"))
    }

    @Test
    fun priceForAdminUsesBasePrice() {
        val pkg = pkg(resellerPrice = "80.00", dealerPrice = "70.00", basePrice = "100.00")
        assertEquals("100.00", pkg.priceFor("admin"))
    }

    @Test
    fun priceForFallsBackToBasePriceWhenResellerPriceNull() {
        val pkg = pkg(resellerPrice = null, basePrice = "100.00")
        assertEquals("100.00", pkg.priceFor("reseller"))
    }

    @Test
    fun isVisibleForResellerChecksResellerFlag() {
        assertTrue(pkg(visibleToReseller = true).isVisibleFor("reseller"))
        assertFalse(pkg(visibleToReseller = false).isVisibleFor("reseller"))
    }

    @Test
    fun isVisibleForDealerChecksDealerFlag() {
        assertTrue(pkg(visibleToDealer = true).isVisibleFor("dealer"))
        assertFalse(pkg(visibleToDealer = false).isVisibleFor("dealer"))
    }

    @Test
    fun matchesReturnsTrueForBlankQuery() {
        assertTrue(pkg().matches(""))
    }

    @Test
    fun matchesReturnsTrueForMatchingName() {
        assertTrue(pkg(name = "Europe 50GB").matches("Europe"))
    }

    @Test
    fun matchesReturnsFalseForNonMatchingQuery() {
        assertFalse(pkg(name = "Europe 50GB", country = "France").matches("Turkey"))
    }

    @Test
    fun specsJoinsDataValidityNetwork() {
        val pkg = pkg(dataAmount = "50GB", validity = "30 Days", network = "4G/5G")
        assertEquals("50GB - 30 Days - 4G/5G", pkg.specs())
    }

    @Test
    fun specsSkipsNullFields() {
        val pkg = pkg(dataAmount = "50GB", validity = null, network = "4G/5G")
        assertEquals("50GB - 4G/5G", pkg.specs())
    }

    private fun pkg(
        name: String = "Test Package",
        country: String = "France",
        basePrice: String? = "100.00",
        resellerPrice: String? = null,
        dealerPrice: String? = null,
        visibleToReseller: Boolean = true,
        visibleToDealer: Boolean = true,
        dataAmount: String? = null,
        validity: String? = null,
        network: String? = null
    ): MobilePackage = MobilePackage(
        id = "1",
        provider = "TestProvider",
        packageType = "esim",
        name = name,
        country = country,
        countryCode = null,
        dataAmount = dataAmount,
        validity = validity,
        basePrice = basePrice,
        resellerPrice = resellerPrice,
        dealerPrice = dealerPrice,
        description = null,
        network = network,
        coverage = null,
        visibleToReseller = visibleToReseller,
        visibleToDealer = visibleToDealer,
        featured = false
    )
}
