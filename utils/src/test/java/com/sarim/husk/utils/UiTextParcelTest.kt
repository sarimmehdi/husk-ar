package com.sarim.husk.utils

import android.os.Parcel
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.Serializable

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
internal class UiTextParcelTest {
    @Test
    fun `dynamic string parcels its value`() {
        val original = UiText.DynamicString("Hello")

        val restored = roundTrip(original) as UiText.DynamicString

        assertEquals(0, original.describeContents())
        assertEquals(original, restored)
    }

    @Test
    fun `string resource parcels resource and arguments`() {
        val original =
            UiText.StringResource(
                resId = R.string.error_unknown,
                args = arrayOf<Serializable>("first", 2),
            )

        val restored = roundTrip(original) as UiText.StringResource

        assertEquals(0, original.describeContents())
        assertEquals(original.resId, restored.resId)
        assertArrayEquals(original.args, restored.args)
    }

    private fun roundTrip(original: UiText): UiText {
        val parcel = Parcel.obtain()
        return try {
            parcel.writeParcelable(original, 0)
            parcel.setDataPosition(0)
            requireNotNull(
                parcel.readParcelable(
                    UiText::class.java.classLoader,
                    UiText::class.java,
                ),
            )
        } finally {
            parcel.recycle()
        }
    }
}
