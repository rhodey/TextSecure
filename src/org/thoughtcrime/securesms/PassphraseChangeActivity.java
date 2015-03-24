/**
 * Copyright (C) 2011 Whisper Systems
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

import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.thoughtcrime.securesms.crypto.InvalidPassphraseException;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.util.MemoryCleaner;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

/**
 * Activity for changing a user's local encryption passphrase.
 *
 * @author Moxie Marlinspike
 */

public class PassphraseChangeActivity extends PassphraseActivity {

  private EditText originalPassphrase;
  private EditText newPassphrase;
  private EditText repeatPassphrase;
  private TextView originalPassphraseLabel;
  private Button   okButton;
  private Button   cancelButton;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.change_passphrase_activity);

    initializeResources();
  }

  private void initializeResources() {
    this.originalPassphraseLabel = (TextView) findViewById(R.id.old_passphrase_label);
    this.originalPassphrase      = (EditText) findViewById(R.id.old_passphrase      );
    this.newPassphrase           = (EditText) findViewById(R.id.new_passphrase      );
    this.repeatPassphrase        = (EditText) findViewById(R.id.repeat_passphrase   );

    this.okButton                = (Button  ) findViewById(R.id.ok_button           );
    this.cancelButton            = (Button  ) findViewById(R.id.cancel_button       );

    this.okButton.setOnClickListener(new OkButtonClickListener());
    this.cancelButton.setOnClickListener(new CancelButtonClickListener());

    if (TextSecurePreferences.isPasswordDisabled(this)) {
      this.originalPassphrase.setVisibility(View.GONE);
      this.originalPassphraseLabel.setVisibility(View.GONE);
    } else {
      this.originalPassphrase.setVisibility(View.VISIBLE);
      this.originalPassphraseLabel.setVisibility(View.VISIBLE);
    }
  }

  private class PassphraseChangeAsyncTask extends AsyncTask<Void, Void, Integer> {

    @Override
    protected Integer doInBackground(Void... params) {
      Editable originalText = originalPassphrase.getText();
      Editable newText      = newPassphrase.getText();
      Editable repeatText   = repeatPassphrase.getText();

      String original         = (originalText == null ? "" : originalText.toString());
      String passphrase       = (newText      == null ? "" : newText.toString());
      String passphraseRepeat = (repeatText   == null ? "" : repeatText.toString());

      if (TextSecurePreferences.isPasswordDisabled(PassphraseChangeActivity.this)) {
        original = MasterSecretUtil.UNENCRYPTED_PASSPHRASE;
      }

      try {
        if (!passphrase.equals(passphraseRepeat)) {
          newPassphrase.setText("");
          repeatPassphrase.setText("");
          return R.string.PassphraseChangeActivity_passphrases_dont_match_exclamation;
        } else {
          MasterSecret masterSecret = MasterSecretUtil.changeMasterSecretPassphrase(PassphraseChangeActivity.this, original, passphrase);
          TextSecurePreferences.setPasswordDisabled(PassphraseChangeActivity.this, false);

          MemoryCleaner.clean(original);
          MemoryCleaner.clean(passphrase);
          MemoryCleaner.clean(passphraseRepeat);

          setMasterSecret(masterSecret);
        }
      } catch (InvalidPassphraseException e) {
        originalPassphrase.setText("");
        return R.string.PassphraseChangeActivity_incorrect_old_passphrase_exclamation;
      }

      return null;
    }

    @Override
    public void onPostExecute(Integer errorResId) {
      if (errorResId != null) {
        Toast.makeText(PassphraseChangeActivity.this, errorResId, Toast.LENGTH_SHORT).show();
      }
    }
  }

  private class CancelButtonClickListener implements OnClickListener {
    public void onClick(View v) {
      finish();
    }
  }

  private class OkButtonClickListener implements OnClickListener {
    public void onClick(View v) {
      new PassphraseChangeAsyncTask().execute();
    }
  }

  @Override
  protected void cleanup() {
    this.originalPassphrase = null;
    this.newPassphrase      = null;
    this.repeatPassphrase   = null;

    System.gc();
  }
}
