package it.feio.android.omninotes;


import android.support.test.espresso.Espresso;
import android.support.test.espresso.ViewInteraction;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.LargeTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.closeSoftKeyboard;
import static android.support.test.espresso.action.ViewActions.typeText;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.hasDescendant;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;


@LargeTest
@RunWith(AndroidJUnit4.class)
public class NotesActionsTest extends BaseEspressoTest {

    @Test
    public void tapFab_DisplaysCreateNoteOptionsButtons() {
        final ViewInteraction fabInteractor = onView(withId(R.id.fab));
        fabInteractor.check(matches(isDisplayed()));
        fabInteractor.perform(click());
        // validate that the three buttons are displayed
        onView(withId(R.id.fab_note)).check(matches(isDisplayed()));
        onView(withId(R.id.fab_checklist)).check(matches(isDisplayed()));
        onView(withId(R.id.fab_camera)).check(matches(isDisplayed()));

    }

    @Test
    public void addSingleNoteWithTitleAndContent() {
        final String note_title = "Note Title";
        final String note_contents = "Note Contents";
        openAddTextNoteView();
        onView(withId(R.id.detail_title)).perform(typeText(note_title), closeSoftKeyboard());
        onView(withId(R.id.detail_content)).perform(typeText(note_contents), closeSoftKeyboard());
        Espresso.pressBack();
        // verify that new note is in the notes list
        onView(withId(R.id.list)).check(matches(hasDescendant(withText(note_title))));
        onView(withId(R.id.list)).check(matches(hasDescendant(withText(note_contents))));
    }

    private void openAddTextNoteView() {
        onView(withId(R.id.fab_expand_menu_button)).perform(click());
        onView(withId(R.id.fab_note)).perform(click());
    }
}
