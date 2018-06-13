package it.feio.android.omninotes;


import android.support.test.espresso.ViewInteraction;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.LargeTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;


@LargeTest
@RunWith(AndroidJUnit4.class)
public class NotesActionsTest extends BaseEspressoTest {

    @Test
    public void tapFab_DisplaysCreateNoteOptionsButtons(){
        final ViewInteraction fabInteractor = onView(withId(R.id.fab));
        fabInteractor.check(matches(isDisplayed()));
        fabInteractor.perform(click());
        // validate that the three buttons are displayed
        onView(withId(R.id.fab_note)).check(matches(isDisplayed()));
        onView(withId(R.id.fab_checklist)).check(matches(isDisplayed()));
        onView(withId(R.id.fab_camera)).check(matches(isDisplayed()));

    }
}
