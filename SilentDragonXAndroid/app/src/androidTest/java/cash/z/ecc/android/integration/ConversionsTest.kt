package cash.z.ecc.android.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import cash.z.ecc.android.ext.WalletZecFormmatter
import cash.z.ecc.android.sdk.model.Zatoshi
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversionsTest {

    @Test
    fun testToZatoshi() {
        val input = "1"
        val result = WalletZecFormmatter.toZatoshi(input)
        Assert.assertEquals(100_000_000L, result)
    }

    @Test
    fun testToZecString_short() {
        val input = Zatoshi(112_340_000L)
        val result = WalletZecFormmatter.toZecStringShort(input)
        Assert.assertEquals("1.1234", result)
    }

    @Test
    fun testToZecString_shortRoundUp() {
        val input = Zatoshi(112_355_600L)
        val result = WalletZecFormmatter.toZecStringShort(input)
        Assert.assertEquals("1.1236", result)
    }

    @Test
    fun testToZecString_shortRoundDown() {
        val input = Zatoshi(112_343_999L)
        val result = WalletZecFormmatter.toZecStringShort(input)
        Assert.assertEquals("1.1234", result)
    }

    @Test
    fun testToZecString_shortRoundHalfEven() {
        val input = Zatoshi(112_345_000L)
        val result = WalletZecFormmatter.toZecStringShort(input)
        Assert.assertEquals("1.1234", result)
    }

    @Test
    fun testToZecString_shortRoundHalfOdd() {
        val input = Zatoshi(112_355_000L)
        val result = WalletZecFormmatter.toZecStringShort(input)
        Assert.assertEquals("1.1236", result)
    }

    @Test
    fun testToBigDecimal_noCommas() {
        val input = "1000"
        val result = WalletZecFormmatter.toBigDecimal(input)!!
        Assert.assertEquals(1000, result.longValueExact())
    }

    @Test
    fun testToBigDecimal_thousandComma() {
        val input = "1,000"
        val result = WalletZecFormmatter.toBigDecimal(input)!!
        Assert.assertEquals(1000, result.longValueExact())
    }

    @Test
    fun testToBigDecimal_thousandCommaWithDecimal() {
        val input = "1,000.00"
        val result = WalletZecFormmatter.toBigDecimal(input)!!
        Assert.assertEquals(1000, result.longValueExact())
    }

    @Test
    fun testToBigDecimal_oneDecimal() {
        val input = "1.000"
        val result = WalletZecFormmatter.toBigDecimal(input)!!
        Assert.assertEquals(1, result.longValueExact())
    }

    @Test
    fun testToBigDecimal_thousandWithThinSpace() {
        val input = "1 000"
        val result = WalletZecFormmatter.toBigDecimal(input)!!
        Assert.assertEquals(1000, result.longValueExact())
    }

    @Test
    fun testToBigDecimal_oneWithThinSpace() {
        val input = "1.000 000"
        val result = WalletZecFormmatter.toBigDecimal(input)!!
        Assert.assertEquals(1, result.longValueExact())
    }

    @Test
    fun testToBigDecimal_oneDecimalWithComma() {
        val input = "1.000,00"
        val result = WalletZecFormmatter.toBigDecimal(input)!!
        Assert.assertEquals(1, result.longValueExact())
    }

    @Test
    fun testToZecString_full() {
        val input = Zatoshi(112_341_123L)
        val result = WalletZecFormmatter.toZecStringFull(input)
        Assert.assertEquals("1.12341123", result)
    }

    @Test
    fun testToZecString_fullRoundUp() {
        val input = Zatoshi(112_355_678L)
        val result = WalletZecFormmatter.toZecStringFull(input)
        Assert.assertEquals("1.12355678", result)
    }

    @Test
    fun testToZecString_fullRoundDown() {
        val input = Zatoshi(112_349_999L)
        val result = WalletZecFormmatter.toZecStringFull(input)
        Assert.assertEquals("1.12349999", result)
    }

    @Test
    fun testToZecString_fullRoundHalfEven() {
        val input = Zatoshi(112_250_009L)
        val result = WalletZecFormmatter.toZecStringFull(input)
        Assert.assertEquals("1.12250009", result)
    }

    @Test
    fun testToZecString_fullRoundHalfOdd() {
        val input = Zatoshi(112_350_004L)
        val result = WalletZecFormmatter.toZecStringFull(input)
        Assert.assertEquals("1.12350004", result)
    }
}
