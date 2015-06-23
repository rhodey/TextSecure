/**
 * Copyright (C) 2015 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms;

import android.content.Context;
import android.content.res.TypedArray;
import android.widget.TextView;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.thoughtcrime.securesms.contacts.ContactSelectionListItem;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.thoughtcrime.securesms.ViewMatchers.withStickyHeadersItem;

public class NewConversationActivityActions {

  public static void filterNameOrNumber(String nameOrNumber) throws Exception {
    EspressoUtil.replaceTextAndCloseKeyboard(onView(withId(R.id.filter)), nameOrNumber);
  }

  @SuppressWarnings("unchecked")
  public static void clickContactWithName(String name) throws Exception {
    onView(allOf(withId(R.id.name), withText(name))).perform(click());
  }

  @SuppressWarnings("unchecked")
  public static void clickContactWithNumber(String number) throws Exception {
    onView(allOf(withId(R.id.number), withText(containsString(number)))).perform(click());
  }

  public static Matcher<Object> isTsContact(final Context context, final String name) {
    return new TypeSafeMatcher<Object>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("is TextSecure contact: " + name);
      }

      @Override
      public boolean matchesSafely(Object object) {
        TypedArray drawables = context.obtainStyledAttributes(new int[] {R.attr.contact_selection_push_user});
        try {
          if (!(object instanceof ContactSelectionListItem)) {
            return false;
          }

          ContactSelectionListItem contactItem = (ContactSelectionListItem) object;
          TextView                 nameView    = (TextView) contactItem.findViewById(R.id.name);
          return nameView.getText().toString().equals(name) &&
                 nameView.getCurrentTextColor() == drawables.getColor(0, 0xa0000000);
        } finally {
          drawables.recycle();
        }
      }
    };
  }

  @SuppressWarnings("unchecked")
  public static void clickTsContactWithName(Context context, String name) {
    onView(withId(android.R.id.list)).check(matches(allOf(
        isDisplayed(),
        withStickyHeadersItem(isTsContact(context, name))
    ))).perform(click());
  }

}
