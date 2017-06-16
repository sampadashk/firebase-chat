/**
 * Copyright Google Inc. All Rights Reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.udacity.MyChat;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    FirebaseDatabase firebaseDatabase;
    DatabaseReference dbrefrence;
    FirebaseStorage firebaseStorage;
    StorageReference storageReference;


    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;
    private static final int RC_PHOTO_PICKER =  2;

    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;
    private ChildEventListener childEventListener;
private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mauthstatelistener;
    private String mUsername;
    public static final int RC_SIGN_IN = 1;

    @Override
    protected void onResume() {
        super.onResume();
        mFirebaseAuth.addAuthStateListener(mauthstatelistener);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        firebaseDatabase=FirebaseDatabase.getInstance();
        dbrefrence=firebaseDatabase.getReference().child("messages");

        mUsername = ANONYMOUS;
        mFirebaseAuth=FirebaseAuth.getInstance();
        firebaseStorage=FirebaseStorage.getInstance();
        storageReference=firebaseStorage.getReference().child("chat_photos");


        // Initialize references to views
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mMessageListView = (ListView) findViewById(R.id.messageListView);
        mPhotoPickerButton = (ImageButton) findViewById(R.id.photoPickerButton);
        mMessageEditText = (EditText) findViewById(R.id.messageEditText);
        mSendButton = (Button) findViewById(R.id.sendButton);

        // Initialize message ListView and its adapter
        List<FriendlyMessage> friendlyMessages = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMessages);
        mMessageListView.setAdapter(mMessageAdapter);

        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(Intent.createChooser(intent, "Complete action using"), RC_PHOTO_PICKER);
            }
        });
        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Send messages on click
                FriendlyMessage friendlyMessage=new FriendlyMessage(mMessageEditText.getText().toString(),mUsername,null);
//putting the value in databse we use setValue if you want to insert it as different child ,use push()
               dbrefrence.push().setValue(friendlyMessage);
                // Clear input box
                mMessageEditText.setText("");
            }
        });


        mauthstatelistener=new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser();
                if(user!=null)
                {
                    onSignedInInitilaize(user.getDisplayName());
                    Toast.makeText(MainActivity.this,"Welcome to firebase Chat!",Toast.LENGTH_LONG).show();

                }
                else
                {
                    onSignedout();
                    //FirebaseUi auth for sign in flow (given by firebase)
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setIsSmartLockEnabled(false).setAvailableProviders(Arrays.asList(new AuthUI.IdpConfig.Builder(AuthUI.EMAIL_PROVIDER).build(),

                                    new AuthUI.IdpConfig.Builder(AuthUI.GOOGLE_PROVIDER).build()) )
                                    .build(),
                            RC_SIGN_IN);

                }

            }
        };
    }

    private void onSignedInInitilaize(String displayName) {
         mUsername=displayName;
        attachListener();

    }

    private void attachListener() {
        if (childEventListener == null) {
            childEventListener = new ChildEventListener() {
                @Override
                public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                    //data is added in db as a child ;this func will be executed;and to get the value from db dataSnapshot.getValue
                    FriendlyMessage fm = dataSnapshot.getValue(FriendlyMessage.class);
                    mMessageAdapter.add(fm);
                }

                @Override
                public void onChildChanged(DataSnapshot dataSnapshot, String s) {

                }

                @Override
                public void onChildRemoved(DataSnapshot dataSnapshot) {

                }

                @Override
                public void onChildMoved(DataSnapshot dataSnapshot, String s) {

                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            };
            dbrefrence.addChildEventListener(childEventListener);
        }
    }

    private void  onSignedout()
    {
        mUsername=ANONYMOUS;
        mMessageAdapter.clear();
        detatchListener();

    }

    private void detatchListener() {
        if(childEventListener!=null)
        {
            dbrefrence.removeEventListener(childEventListener);
            childEventListener=null;
        }
    }
    @Override
    public void onActivityResult(int requestcode,int resultcode,Intent data)
    {
        super.onActivityResult(requestcode,resultcode,data);
        if(requestcode==RC_SIGN_IN) {
            if (resultcode == RESULT_OK) {
                Toast.makeText(this, "Signed in!", Toast.LENGTH_SHORT).show();
            } else if (resultcode == RESULT_CANCELED) {
                Toast.makeText(this, "Signed in cancelled!", Toast.LENGTH_SHORT).show();
                finish();
            }
        }else if (requestcode == RC_PHOTO_PICKER && resultcode == RESULT_OK) {

                Uri photo = data.getData();
                StorageReference photoRef = storageReference.child(photo.getLastPathSegment());
            //storing photo in  firebase storage
                photoRef.putFile(photo).addOnSuccessListener(this, new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        Uri downloadUrl = taskSnapshot.getDownloadUrl();
                        FriendlyMessage fms = new FriendlyMessage(null, mUsername, downloadUrl.toString());
//storing the link of photo(uri) in db
                        dbrefrence.push().setValue(fms);
                    }
                });

            }
        }




    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sign_out_menu:
                AuthUI.getInstance().signOut(this);
                return true;
            default:
            return super.onOptionsItemSelected(item);
        }
    }
    @Override
    protected void onPause()
    {
        super.onPause();
        if(mauthstatelistener!=null) {
            mFirebaseAuth.removeAuthStateListener(mauthstatelistener);
        }
        detatchListener();
        mMessageAdapter.clear();


    }
}
