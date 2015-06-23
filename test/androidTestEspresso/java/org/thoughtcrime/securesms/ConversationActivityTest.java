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

import android.test.suitebuilder.annotation.LargeTest;

import org.thoughtcrime.securesms.test.R;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.longClick;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.thoughtcrime.securesms.EspressoUtil.addContact;
import static org.thoughtcrime.securesms.EspressoUtil.waitOn;

@LargeTest
public class ConversationActivityTest extends TextSecureEspressoTestCase<ConversationListActivity> {

  public ConversationActivityTest() {
    super(ConversationListActivity.class);
  }

  public void testSendRecieiveTextMessage() throws Exception {
    final String   BOT_NAME    = "tsbot00";
    final String[] BOT_NUMBERS = getInstrumentation().getContext().getResources().getStringArray(R.array.test_bot_ptsn_numbers);

    addContact(getContext(), BOT_NAME, BOT_NUMBERS[0]);
    loadActivity(ConversationListActivity.class, STATE_REGISTERED);

    ConversationListActivityActions.clickNewConversation();
    waitOn(NewConversationActivity.class);
    NewConversationActivityActions.clickTsContactWithName(getContext(), BOT_NAME);
    waitOn(ConversationActivity.class);

    ConversationActivityActions.typeMessage("if");
    ConversationActivityActions.clickSend();
    Thread.sleep(10000);
    onView(withText("youre")).check(matches(isDisplayed()));

    ConversationActivityActions.typeMessage("bowing");
    ConversationActivityActions.clickSend();
    Thread.sleep(10000);
    onView(withText("down")).check(matches(isDisplayed()));
  }

  public void testForwardMessage() throws Exception {
    final String[] CONTACT_NAMES   = new String[] {"Clement Duval", "Masha Kolenkia"};
    final String[] CONTACT_NUMBERS = new String[] {"55555555555",   "33333333333"};
    final String   MESSAGE         = "I struck him in the name of liberty";

    addContact(getContext(), CONTACT_NAMES[0], CONTACT_NUMBERS[0]);
    addContact(getContext(), CONTACT_NAMES[1], CONTACT_NUMBERS[1]);
    loadActivity(ConversationListActivity.class, STATE_REGISTERED);

    ConversationListActivityActions.clickNewConversation();
    waitOn(NewConversationActivity.class);
    NewConversationActivityActions.clickContactWithName(CONTACT_NAMES[0]);
    waitOn(ConversationActivity.class);
    ConversationActivityActions.typeMessage(MESSAGE);
    ConversationActivityActions.clickSend();

    onView(withText(MESSAGE)).perform(longClick());
    ConversationActivityActions.clickForwardMessage();

    waitOn(ShareActivity.class);
    ShareActivityActions.clickNewMessage();
    waitOn(NewConversationActivity.class);
    NewConversationActivityActions.filterNameOrNumber(CONTACT_NAMES[1]);
    NewConversationActivityActions.clickContactWithName(CONTACT_NAMES[1]);

    waitOn(ConversationActivity.class);
    onView(withText(MESSAGE)).check(matches(isDisplayed()));
  }

}
