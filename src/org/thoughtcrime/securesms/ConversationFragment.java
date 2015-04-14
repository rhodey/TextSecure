package org.thoughtcrime.securesms;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.view.ActionMode;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.afollestad.materialdialogs.AlertDialogWrapper;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.loaders.ConversationLoader;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.util.DirectoryHelper;
import org.thoughtcrime.securesms.util.FutureTaskListener;
import org.thoughtcrime.securesms.util.ProgressDialogAsyncTask;
import org.thoughtcrime.securesms.util.SaveAttachmentTask;
import org.thoughtcrime.securesms.util.SaveAttachmentTask.Attachment;

import java.util.LinkedList;
import java.util.List;

public class ConversationFragment extends ListFragment
  implements LoaderManager.LoaderCallbacks<Cursor>
{
  private static final String TAG = ConversationFragment.class.getSimpleName();

  private final ActionModeCallback     actionModeCallback     = new ActionModeCallback();
  private final SelectionClickListener selectionClickListener = new ConversationFragmentSelectionClickListener();

  private ConversationFragmentListener listener;

  private MasterSecret masterSecret;
  private Recipients   recipients;
  private long         threadId;
  private ActionMode   actionMode;

  @Override
  public void onCreate(Bundle icicle) {
    Log.d(TAG, "ON CREATE");
    super.onCreate(icicle);
    this.masterSecret = getArguments().getParcelable("master_secret");
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
    Log.d(TAG, "ON CREATE VIEW");
    return inflater.inflate(R.layout.conversation_fragment, container, false);
  }

  @Override
  public void onActivityCreated(Bundle bundle) {
    Log.d(TAG, "ON ACTIVITY CREATED");
    super.onActivityCreated(bundle);

    initializeResources();
    initializeListAdapter();
    initializeContextualActionBar();
  }

  @Override
  public void onAttach(Activity activity) {
    Log.d(TAG, "ON ATTACH");
    super.onAttach(activity);
    this.listener = (ConversationFragmentListener)activity;
  }

  @Override
  public void onResume() {
    Log.d(TAG, "ON RESUME");
    super.onResume();

    if (getListAdapter() != null) {
      Log.d(TAG, "NOT NULL, NOTIFY CHANGE");
      ((ConversationAdapter) getListAdapter()).notifyDataSetChanged();
    }
  }

  public void onNewIntent() {
    Log.d(TAG, "ON NEW INTENT");
    if (actionMode != null) {
      Log.d(TAG, "AM NOT NULL, GONNA FINISH AB");
      actionMode.finish();
    }

    initializeResources();
    initializeListAdapter();

    if (threadId == -1) {
      Log.d(TAG, "THREAD == -1, RESTART LOADER");
      getLoaderManager().restartLoader(0, null, this);
    }
  }

  private void initializeResources() {
    this.recipients   = RecipientFactory.getRecipientsForIds(getActivity(), getActivity().getIntent().getLongArrayExtra("recipients"), true);
    this.threadId     = this.getActivity().getIntent().getLongExtra("thread_id", -1);
    Log.d(TAG, "INIT RESOURCES, " + recipients.toShortString() + " | " + threadId);
  }

  private void initializeListAdapter() {
    Log.d(TAG, "INIT LIST ADAPTER");
    if (this.recipients != null && this.threadId != -1) {
      Log.d(TAG, "**ACTUALLY** INIT LIST ADAPTER");
      this.setListAdapter(new ConversationAdapter(getActivity(), masterSecret, selectionClickListener,
                                                  (!this.recipients.isSingleRecipient()) || this.recipients.isGroupRecipient(),
                                                  DirectoryHelper.isPushDestination(getActivity(), this.recipients)));
      getListView().setRecyclerListener((ConversationAdapter)getListAdapter());
      getLoaderManager().restartLoader(0, null, this);
    }
  }

  private void initializeContextualActionBar() {
    Log.d(TAG, "INIT CONTEXT AB");
    getListView().setOnItemClickListener(selectionClickListener);
    getListView().setOnItemLongClickListener(selectionClickListener);
  }

  private void setCorrectMenuVisibility(Menu menu) {
    Log.d(TAG, "SET CORRECT MENU VISIBILITY");
    List<MessageRecord> messageRecords = getSelectedMessageRecords();

    if (actionMode != null && messageRecords.size() == 0) {
      actionMode.finish();
      return;
    }

    if (messageRecords.size() > 1) {
      menu.findItem(R.id.menu_context_forward).setVisible(false);
      menu.findItem(R.id.menu_context_copy).setVisible(false);
      menu.findItem(R.id.menu_context_details).setVisible(false);
      menu.findItem(R.id.menu_context_save_attachment).setVisible(false);
      menu.findItem(R.id.menu_context_resend).setVisible(false);
    } else {
      MessageRecord messageRecord = messageRecords.get(0);

      menu.findItem(R.id.menu_context_resend).setVisible(messageRecord.isFailed());
      menu.findItem(R.id.menu_context_save_attachment).setVisible(messageRecord.isMms()              &&
                                                                  !messageRecord.isMmsNotification() &&
                                                                  ((MediaMmsMessageRecord)messageRecord).containsMediaSlide());

      menu.findItem(R.id.menu_context_forward).setVisible(true);
      menu.findItem(R.id.menu_context_details).setVisible(true);
      menu.findItem(R.id.menu_context_copy).setVisible(true);
    }
  }

  private MessageRecord getSelectedMessageRecord() {
    List<MessageRecord> messageRecords = getSelectedMessageRecords();

    if (messageRecords.size() == 1) return messageRecords.get(0);
    else                            throw new AssertionError();
  }

  private List<MessageRecord> getSelectedMessageRecords() {
    return new LinkedList<>(((ConversationAdapter)getListAdapter()).getBatchSelected());
  }

  public void reload(Recipients recipients, long threadId) {
    Log.d(TAG, "RELOAD >> " + recipients.toShortString());
    this.recipients = recipients;

    if (this.threadId != threadId) {
      Log.d(TAG, "THREAD ID CHANGED, GONNA RE-INIT LIST >> " + threadId);
      this.threadId = threadId;
      initializeListAdapter();
    }
  }

  public void scrollToBottom() {
    Log.d(TAG, "SCROLL TO BOTTOM");
    final ListView list = getListView();
    list.post(new Runnable() {
      @Override
      public void run() {
        list.setSelection(getListAdapter().getCount() - 1);
      }
    });
  }

  private void handleCopyMessage(MessageRecord message) {
    String body = message.getDisplayBody().toString();
    if (body == null) return;

    ClipboardManager clipboard = (ClipboardManager)getActivity()
        .getSystemService(Context.CLIPBOARD_SERVICE);
    clipboard.setText(body);
  }

  private void handleDeleteMessages(final List<MessageRecord> messageRecords) {
    Log.d(TAG, "HANDLE DELETE MESSAGE");
    AlertDialogWrapper.Builder builder = new AlertDialogWrapper.Builder(getActivity());
    builder.setTitle(R.string.ConversationFragment_confirm_message_delete);
    builder.setIconAttribute(R.attr.dialog_alert_icon);
    builder.setCancelable(true);
    builder.setMessage(R.string.ConversationFragment_are_you_sure_you_want_to_permanently_delete_all_selected_messages);
    builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        new ProgressDialogAsyncTask<MessageRecord, Void, Void>(getActivity(),
                                                               R.string.ConversationFragment_deleting,
                                                               R.string.ConversationFragment_deleting_messages)
        {
          @Override
          protected Void doInBackground(MessageRecord... messageRecords) {
            for (MessageRecord messageRecord : messageRecords) {
              if (messageRecord.isMms()) {
                DatabaseFactory.getMmsDatabase(getActivity()).delete(messageRecord.getId());
              } else {
                DatabaseFactory.getSmsDatabase(getActivity()).deleteMessage(messageRecord.getId());
              }
            }

            return null;
          }
        }.execute(messageRecords.toArray(new MessageRecord[messageRecords.size()]));
      }
    });

    builder.setNegativeButton(R.string.no, null);
    builder.show();
  }

  private void handleDisplayDetails(MessageRecord message) {
    Log.d(TAG, "HANDLE DISPLAY DETAILS");
    Intent intent = new Intent(getActivity(), MessageDetailsActivity.class);
    intent.putExtra(MessageDetailsActivity.MASTER_SECRET_EXTRA, masterSecret);
    intent.putExtra(MessageDetailsActivity.MESSAGE_ID_EXTRA, message.getId());
    intent.putExtra(MessageDetailsActivity.TYPE_EXTRA, message.isMms() ? MmsSmsDatabase.MMS_TRANSPORT : MmsSmsDatabase.SMS_TRANSPORT);
    startActivity(intent);
  }

  private void handleForwardMessage(MessageRecord message) {
    Log.d(TAG, "HANDLE FORWARD MESSAGE");
    Intent composeIntent = new Intent(getActivity(), ShareActivity.class);
    composeIntent.putExtra(Intent.EXTRA_TEXT, message.getDisplayBody().toString());
    startActivity(composeIntent);
  }

  private void handleResendMessage(final MessageRecord message) {
    Log.d(TAG, "HANDLE RESEND MESSAGE");
    final Context context = getActivity().getApplicationContext();
    new AsyncTask<MessageRecord, Void, Void>() {
      @Override
      protected Void doInBackground(MessageRecord... messageRecords) {
        MessageSender.resend(context, masterSecret, messageRecords[0]);
        return null;
      }
    }.execute(message);
  }

  private void handleSaveAttachment(final MediaMmsMessageRecord message) {
    Log.d(TAG, "HANDLE SAVE ATTACH");
    SaveAttachmentTask.showWarningDialog(getActivity(), new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {

        message.fetchMediaSlide(new FutureTaskListener<Slide>() {
          @Override
          public void onSuccess(Slide slide) {
            SaveAttachmentTask saveTask = new SaveAttachmentTask(getActivity(), masterSecret);
            saveTask.execute(new Attachment(slide.getUri(), slide.getContentType(), message.getDateReceived()));
          }

          @Override
          public void onFailure(Throwable error) {
            Log.w(TAG, "No slide with attachable media found, failing nicely.");
            Log.w(TAG, error);
            Toast.makeText(getActivity(), R.string.ConversationFragment_error_while_saving_attachment_to_sd_card, Toast.LENGTH_LONG).show();
          }
        });
      }
    });
  }

  @Override
  public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
    Log.d(TAG, "ON CREATE LOADER");
    return new ConversationLoader(getActivity(), threadId);
  }

  @Override
  public void onLoadFinished(Loader<Cursor> arg0, Cursor cursor) {
    Log.d(TAG, "ON LOAD FINISHED");
    if (getListAdapter() != null) {
      ((CursorAdapter) getListAdapter()).changeCursor(cursor);
    } else {
      Log.d(TAG, "LIST ADAPTER IS NULL D:");
    }
  }

  @Override
  public void onLoaderReset(Loader<Cursor> arg0) {
    Log.d(TAG, "ON LOAD RESET");
    if (getListAdapter() != null) {
      ((CursorAdapter) getListAdapter()).changeCursor(null);
    } else {
      Log.d(TAG, "LIST ADAPTER IS NULL D:");
    }
  }

  public interface ConversationFragmentListener {
    public void setComposeText(String text);
  }

  public interface SelectionClickListener extends
      AdapterView.OnItemLongClickListener, AdapterView.OnItemClickListener {}

  private class ConversationFragmentSelectionClickListener
      implements SelectionClickListener
  {
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
      if (actionMode != null && view instanceof ConversationItem) {
        MessageRecord messageRecord = ((ConversationItem)view).getMessageRecord();
        ((ConversationAdapter) getListAdapter()).toggleBatchSelected(messageRecord);
        ((ConversationAdapter) getListAdapter()).notifyDataSetChanged();

        setCorrectMenuVisibility(actionMode.getMenu());
      }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
      if (actionMode == null && view instanceof ConversationItem) {
        MessageRecord messageRecord = ((ConversationItem)view).getMessageRecord();
        ((ConversationAdapter) getListAdapter()).toggleBatchSelected(messageRecord);
        ((ConversationAdapter) getListAdapter()).notifyDataSetChanged();

        actionMode = ((ActionBarActivity)getActivity()).startSupportActionMode(actionModeCallback);
        return true;
      }

      return false;
    }
  }

  private class ActionModeCallback implements ActionMode.Callback {

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
      Log.d(TAG, "ON CREATE AM");
      MenuInflater inflater = mode.getMenuInflater();
      inflater.inflate(R.menu.conversation_context, menu);

      setCorrectMenuVisibility(menu);
      return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
      return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
      Log.d(TAG, "ON DESTORY AM");
      ((ConversationAdapter)getListAdapter()).getBatchSelected().clear();
      ((ConversationAdapter)getListAdapter()).notifyDataSetChanged();

      actionMode = null;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
      switch(item.getItemId()) {
        case R.id.menu_context_copy:
          handleCopyMessage(getSelectedMessageRecord());
          actionMode.finish();
          return true;
        case R.id.menu_context_delete_message:
          handleDeleteMessages(getSelectedMessageRecords());
          actionMode.finish();
          return true;
        case R.id.menu_context_details:
          handleDisplayDetails(getSelectedMessageRecord());
          actionMode.finish();
          return true;
        case R.id.menu_context_forward:
          handleForwardMessage(getSelectedMessageRecord());
          actionMode.finish();
          return true;
        case R.id.menu_context_resend:
          handleResendMessage(getSelectedMessageRecord());
          actionMode.finish();
          return true;
        case R.id.menu_context_save_attachment:
          handleSaveAttachment((MediaMmsMessageRecord)getSelectedMessageRecord());
          actionMode.finish();
          return true;
      }

      return false;
    }
  };
}
