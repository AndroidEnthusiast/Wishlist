package com.wishlist.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Screenshot to wish (owner, 20 November 2026; docs/decisions/screenshots.md): one picture goes straight to the editor;
 * several get a pick screen, then one by one with Skip; a picture that can't be read is skipped; each saved wish is
 * counted and the end message names the list; Stop keeps what was saved.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShotsModelTest {
    private val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun state(vararg pics: String) = ShotsState(ctx, pics.toList(), null, false).also { Repo.load(ctx) }
    // A pretend copy on the phone; "bad" can't be read.
    private suspend fun ShotsState.loadFake() = load { u -> if (u.toString().contains("bad")) null else "file:///x/" + u.lastPathSegment + ".jpg" }

    @Test fun oneScreenshotGoesStraightToTheEditor() = runBlocking {
        val s = state("content://m/1")
        s.loadFake()
        assertEquals(ShotStep.PHOTO, s.step)
        assertFalse(s.several)
        s.photoDone("file:///x/cut.jpg")
        assertEquals(ShotStep.FORM, s.step)
        s.saved("Shoes", "mine", "My wishlist")
        assertEquals(ShotStep.DONE, s.step)
        assertEquals("“Shoes” added to My wishlist", s.doneMessage())
    }

    @Test fun severalArePickedThenAddedOneByOne() = runBlocking {
        val s = state("content://m/1", "content://m/bad", "content://m/3", "content://m/4")
        s.loadFake()
        assertEquals(ShotStep.PICK, s.step)
        assertTrue("a picture that can't be read is marked and unticked", s.shots[1].failed && !s.shots[1].picked)
        assertEquals(3, s.pickedCount)
        s.toggle(s.shots[1]); assertFalse("it can't be ticked", s.shots[1].picked)
        s.toggle(s.shots[3]); assertEquals(2, s.pickedCount)
        s.begin()
        assertEquals(listOf(s.shots[0], s.shots[2]), s.queue)
        assertEquals(ShotStep.PHOTO, s.step)
        s.skip()
        assertEquals(1, s.index); assertEquals(ShotStep.PHOTO, s.step)
        s.photoDone("file:///x/cut.jpg"); s.saved("Lamp", "mine:abc", "Home")
        assertEquals(ShotStep.DONE, s.step)
        assertEquals("“Lamp” added to Home", s.doneMessage())
    }

    @Test fun theEndMessageCountsAndNamesTheList() = runBlocking {
        val s = state("content://m/1", "content://m/2", "content://m/3")
        s.loadFake(); s.begin()
        s.photoDone("a"); s.saved("A", "mine", "My wishlist")
        assertEquals("“A” added to My wishlist", s.notice)
        s.photoDone("b"); s.saved("B", "mine", "My wishlist")
        s.photoDone("c"); s.saved("C", "mine", "My wishlist")
        assertEquals("3 wishes added to My wishlist", s.doneMessage())
        assertEquals("m:" + MAIN_LIST, routeOfSaveKey("mine"))
        assertEquals("m:abc", routeOfSaveKey("mine:abc"))
        assertEquals("s:ABCDEFGH", routeOfSaveKey("ABCDEFGH"))
    }

    @Test fun twoListsMeansNoListInTheMessage() = runBlocking {
        val s = state("content://m/1", "content://m/2")
        s.loadFake(); s.begin()
        s.photoDone("a"); s.saved("A", "mine", "My wishlist")
        s.photoDone("b"); s.saved("B", "ABCDEFGH", "Gifts for Mum")
        assertEquals("2 wishes added", s.doneMessage())
        assertEquals(2, s.lists.size)
    }

    @Test fun cancelAsksWithSeveralAndStopKeepsWhatWasSaved() = runBlocking {
        val s = state("content://m/1", "content://m/2", "content://m/3")
        s.loadFake(); s.begin()
        s.photoDone("a"); s.saved("A", "mine", "My wishlist")
        s.cancel()
        assertTrue(s.askStop); assertEquals(ShotStep.PHOTO, s.step)
        assertEquals(2, s.left)
        s.stop()
        assertEquals(ShotStep.DONE, s.step); assertFalse(s.askStop)
        assertEquals(1, s.added)
        assertEquals("“A” added to My wishlist", s.doneMessage())
    }

    @Test fun oneCancelledOrNoneReadableAddsNothing() = runBlocking {
        val one = state("content://m/1")
        one.loadFake(); one.cancel()
        assertEquals(ShotStep.DONE, one.step); assertFalse(one.askStop); assertNull(one.doneMessage())
        val bad = state("content://m/bad")
        bad.loadFake()
        assertEquals(ShotStep.DONE, bad.step)
        assertTrue(bad.shots.all { it.failed })
    }
}
