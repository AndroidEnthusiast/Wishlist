package com.wishlist.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Text in chips and buttons sits exactly in the middle between top and bottom (owner, 17 November 2026: "in the middle
 * compared to the top and bottom of the chip"). Measured from Plus Jakarta Sans' own font file (ascent 1.038, descent
 * 0.222, capitals 0.745 of the size): with the spare line height split this way, the capitals' centre is the line's
 * centre. Bricolage Grotesque (Gen Z chaos) measured centred already.
 */
class ChipCentreTest {
    @Test fun capitalsSitInTheMiddleOfEveryLabel() {
        for ((size, line) in listOf(15 to 20, 13 to 18, 12 to 16)) {
            val spare = line - size * (JAKARTA_ASCENT + JAKARTA_DESCENT)
            val capsCentre = capsTopRatio(size, line) * spare + size * (JAKARTA_ASCENT - JAKARTA_CAP / 2f)
            assertEquals("label $size/$line", line / 2f, capsCentre, 0.01f)
        }
    }

    @Test fun quietLabelsUseIt() {
        val t = typographyFor("quiet")
        for (st in listOf(t.labelLarge, t.labelMedium, t.labelSmall)) assertNotNull(st.lineHeightStyle)
    }
}
