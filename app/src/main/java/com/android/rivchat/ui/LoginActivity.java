package com.android.rivchat.ui;

import android.annotation.SuppressLint;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.android.rivchat.util.CallService;
import com.android.rivchat.util.Consts;
import com.android.rivchat.util.SharedPrefsHelper;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.android.rivchat.MainActivity;
import com.android.rivchat.R;
import com.android.rivchat.data.SharedPreferenceHelper;
import com.android.rivchat.data.StaticConfig;
import com.android.rivchat.model.User;
import com.quickblox.auth.session.QBSettings;
import com.quickblox.chat.QBChatService;
import com.quickblox.core.QBEntityCallback;
import com.quickblox.core.ServiceZone;
import com.quickblox.core.exception.QBResponseException;
import com.quickblox.core.helper.StringifyArrayList;
import com.quickblox.users.QBUsers;
import com.quickblox.users.model.QBUser;
import com.yarolegovich.lovelydialog.LovelyInfoDialog;
import com.yarolegovich.lovelydialog.LovelyProgressDialog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class LoginActivity extends AppCompatActivity {
    private static String TAG = "LoginActivity";
    FloatingActionButton fab;
    private final Pattern VALID_EMAIL_ADDRESS_REGEX =
            Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$", Pattern.CASE_INSENSITIVE);
    private EditText editTextUsername, editTextPassword;
    private LovelyProgressDialog waitingDialog;

    private AuthUtils authUtils;
    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;
    private FirebaseUser user;
    private boolean firstTimeAccess;
    static final String APP_ID = "68832";
    static final String AUTH_KEY = "Rp3VNHXD-Ca2nY3";
    static final String AUTH_SECRET = "nWkbdBkErazPf-c";
    static final String ACCOUNT_KEY = "vgrCdLswvR-8fVU5uKs2";
    static final String API_ENDPOINT = "api.quickblox.com";
    static final String CHAT_ENDPOINT = "chat.quickblox.com";

    @Override
    protected void onStart() {
        super.onStart();
        mAuth.addAuthStateListener(mAuthListener);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        fab = (FloatingActionButton) findViewById(R.id.fab);
        editTextUsername = (EditText) findViewById(R.id.et_username);
        editTextPassword = (EditText) findViewById(R.id.et_password);
        firstTimeAccess = true;
        initFirebase();
        initQuickBlox();
    }

    private void initQuickBlox() {
        QBSettings.getInstance().init(this,
                APP_ID,
                AUTH_KEY,
                AUTH_SECRET);
        QBSettings.getInstance().setAccountKey(ACCOUNT_KEY);
        QBSettings.getInstance().setEndpoints(API_ENDPOINT,
                CHAT_ENDPOINT,
                ServiceZone.DEVELOPMENT);
        QBSettings.getInstance().setZone(ServiceZone.DEVELOPMENT);
    }


    /**
     * Khởi tạo các thành phần cần thiết cho việc quản lý đăng nhập
     */
    private void initFirebase() {
        //Khoi tao thanh phan de dang nhap, dang ky
        mAuth = FirebaseAuth.getInstance();
        authUtils = new AuthUtils();
        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    // User is signed in
                    StaticConfig.UID = user.getUid();
                    Log.d(TAG, "onAuthStateChanged:signed_in:" + user.getUid());
                    if (firstTimeAccess) {
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        LoginActivity.this.finish();
                    }
                } else {
                    Log.d(TAG, "onAuthStateChanged:signed_out");
                }
                firstTimeAccess = false;
            }
        };

        //Khoi tao dialog waiting khi dang nhap
        waitingDialog = new LovelyProgressDialog(this).setCancelable(false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mAuthListener != null) {
            mAuth.removeAuthStateListener(mAuthListener);
        }
    }

    @SuppressLint("RestrictedApi")
    public void clickRegisterLayout(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setExitTransition(null);
            getWindow().setEnterTransition(null);
        }


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ActivityOptions options =
                    ActivityOptions.makeSceneTransitionAnimation(this, fab, fab.getTransitionName());
            startActivityForResult(new Intent(this, RegisterActivity.class), StaticConfig.REQUEST_CODE_REGISTER, options.toBundle());
        } else {
            startActivityForResult(new Intent(this, RegisterActivity.class), StaticConfig.REQUEST_CODE_REGISTER);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == StaticConfig.REQUEST_CODE_REGISTER && resultCode == RESULT_OK) {
            authUtils.createUser(data.getStringExtra(StaticConfig.STR_EXTRA_USERNAME), data.getStringExtra(StaticConfig.STR_EXTRA_PASSWORD));
        }
    }

    public void clickLogin(View view) {
        String username = editTextUsername.getText().toString();
        String password = editTextPassword.getText().toString();
        if (validate(username, password)) {
            authUtils.signIn(username, password);
        } else {
            Toast.makeText(this, "Invalid email or empty password", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        setResult(RESULT_CANCELED, null);
        finish();
    }

    private boolean validate(String emailStr, String password) {
        Matcher matcher = VALID_EMAIL_ADDRESS_REGEX.matcher(emailStr);
        return (password.length() > 0 || password.equals(";")) && matcher.find();
    }

    public void clickResetPassword(View view) {
        String username = editTextUsername.getText().toString();
        if (validate(username, ";")) {
            authUtils.resetPassword(username);
        } else {
            Toast.makeText(this, "Invalid email", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Dinh nghia cac ham tien ich cho quas trinhf dang nhap, dang ky,...
     */
    class AuthUtils {


        void startLoginService(QBUser qbUser) {
            Intent tempIntent = new Intent(LoginActivity.this, CallService.class);
            PendingIntent pendingIntent = createPendingResult(Consts.EXTRA_LOGIN_RESULT_CODE, tempIntent, 0);
            CallService.start(LoginActivity.this, qbUser, pendingIntent);
        }

        void saveUserData(QBUser qbUser) {
            SharedPrefsHelper sharedPrefsHelper = SharedPrefsHelper.getInstance(LoginActivity.this);
            sharedPrefsHelper.save(Consts.PREF_CURREN_ROOM_NAME, qbUser.getTags().get(0));
            sharedPrefsHelper.saveQbUser(qbUser);
        }

        void createUser(final String email, final String password) {
            waitingDialog.setIcon(R.drawable.ic_add_friend)
                    .setTitle("Registering....")
                    .setTopColorRes(R.color.colorPrimary)
                    .show();
            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(LoginActivity.this, new OnCompleteListener<AuthResult>() {
                        @Override
                        public void onComplete(@NonNull Task<AuthResult> task) {
                            Log.d(TAG, "createUserWithEmail:onComplete:" + task.isSuccessful());
                            waitingDialog.dismiss();
                            // If sign in fails, display a message to the user. If sign in succeeds
                            // the auth state listener will be notified and logic to handle the
                            // signed in user can be handled in the listener.
                            if (!task.isSuccessful()) {
                                new LovelyInfoDialog(LoginActivity.this) {
                                    @Override
                                    public LovelyInfoDialog setConfirmButtonText(String text) {
                                        findView(com.yarolegovich.lovelydialog.R.id.ld_btn_confirm).setOnClickListener(new View.OnClickListener() {
                                            @Override
                                            public void onClick(View view) {
                                                dismiss();
                                            }
                                        });
                                        return super.setConfirmButtonText(text);
                                    }
                                }
                                        .setTopColorRes(R.color.colorAccent)
                                        .setIcon(R.drawable.ic_add_friend)
                                        .setTitle("Register false")
                                        .setMessage("Email exist or weak password!")
                                        .setConfirmButtonText("ok")
                                        .setCancelable(false)
                                        .show();
                            } else {
                                final QBUser user = new QBUser();
                                user.setLogin(email);
                                user.setPassword(password);
                                StringifyArrayList<String> tag = new StringifyArrayList<>();
                                tag.add("bunda");
                                user.setTags(tag);
                                user.setEmail(email);
                                QBUsers.signUp(user).performAsync(new QBEntityCallback<QBUser>() {
                                    @Override
                                    public void onSuccess(QBUser qbUser, Bundle bundle) {
                                        user.setId(qbUser.getId());
                                        initNewUserInfo();
                                        Toast.makeText(LoginActivity.this, "Register and Login success", Toast.LENGTH_SHORT).show();
                                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                                        startLoginService(qbUser);
                                        saveUserData(qbUser);
                                        LoginActivity.this.finish();

                                    }


                                    @Override
                                    public void onError(QBResponseException e) {
                                        new LovelyInfoDialog(LoginActivity.this) {
                                            @Override
                                            public LovelyInfoDialog setConfirmButtonText(String text) {
                                                findView(com.yarolegovich.lovelydialog.R.id.ld_btn_confirm).setOnClickListener(new View.OnClickListener() {
                                                    @Override
                                                    public void onClick(View view) {
                                                        dismiss();
                                                    }
                                                });
                                                return super.setConfirmButtonText(text);
                                            }
                                        }
                                                .setTopColorRes(R.color.colorAccent)
                                                .setIcon(R.drawable.ic_add_friend)
                                                .setTitle("Register false")
                                                .setMessage("Email exist or weak password!")
                                                .setConfirmButtonText("ok")
                                                .setCancelable(false)
                                                .show();
                                    }
                                });

                            }
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            waitingDialog.dismiss();
                        }
                    });
        }

        void signIn(final String email, final String password) {
            waitingDialog.setIcon(R.drawable.ic_person_low)
                    .setTitle("Login....")
                    .setTopColorRes(R.color.colorPrimary)
                    .show();
            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(LoginActivity.this, new OnCompleteListener<AuthResult>() {
                        @Override
                        public void onComplete(@NonNull final Task<AuthResult> task) {
                            Log.d(TAG, "signInWithEmail:onComplete:" + task.isSuccessful());
                            // If sign in fails, display a message to the user. If sign in succeeds
                            // the auth state listener will be notified and logic to handle the
                            // signed in user can be handled in the listener.
                            waitingDialog.dismiss();
                            if (!task.isSuccessful()) {
                                Log.w(TAG, "signInWithEmail:failed", task.getException());
                                new LovelyInfoDialog(LoginActivity.this) {
                                    @Override
                                    public LovelyInfoDialog setConfirmButtonText(String text) {
                                        findView(com.yarolegovich.lovelydialog.R.id.ld_btn_confirm).setOnClickListener(new View.OnClickListener() {
                                            @Override
                                            public void onClick(View view) {
                                                dismiss();
                                            }
                                        });
                                        return super.setConfirmButtonText(text);
                                    }
                                }
                                        .setTopColorRes(R.color.colorAccent)
                                        .setIcon(R.drawable.ic_person_low)
                                        .setTitle("Login false")
                                        .setMessage("Email not exist or wrong password!")
                                        .setCancelable(false)
                                        .setConfirmButtonText("Ok")
                                        .show();
                            } else {
                                final QBUser user = new QBUser(email, password);
                                QBUsers.signIn(user).performAsync(new QBEntityCallback<QBUser>() {
                                    @Override
                                    public void onSuccess(QBUser qbUser, Bundle bundle) {
                                        user.setId(qbUser.getId());
                                        saveUserInfo();
                                        startLoginService(qbUser);
                                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                                        LoginActivity.this.finish();
                                    }

                                    @Override
                                    public void onError(QBResponseException e) {
                                        Log.w(TAG, "signInWithEmail:failed", task.getException());
                                        new LovelyInfoDialog(LoginActivity.this) {
                                            @Override
                                            public LovelyInfoDialog setConfirmButtonText(String text) {
                                                findView(com.yarolegovich.lovelydialog.R.id.ld_btn_confirm).setOnClickListener(new View.OnClickListener() {
                                                    @Override
                                                    public void onClick(View view) {
                                                        dismiss();
                                                    }
                                                });
                                                return super.setConfirmButtonText(text);
                                            }
                                        }
                                                .setTopColorRes(R.color.colorAccent)
                                                .setIcon(R.drawable.ic_person_low)
                                                .setTitle("Login false")
                                                .setMessage("Email not exist or wrong password!")
                                                .setCancelable(false)
                                                .setConfirmButtonText("Ok")
                                                .show();
                                    }
                                });

                            }
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            waitingDialog.dismiss();
                        }
                    });
        }

        /**
         * Action reset password
         *
         * @param email
         */
        void resetPassword(final String email) {
            mAuth.sendPasswordResetEmail(email)
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            new LovelyInfoDialog(LoginActivity.this) {
                                @Override
                                public LovelyInfoDialog setConfirmButtonText(String text) {
                                    findView(com.yarolegovich.lovelydialog.R.id.ld_btn_confirm).setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View view) {
                                            dismiss();
                                        }
                                    });
                                    return super.setConfirmButtonText(text);
                                }
                            }
                                    .setTopColorRes(R.color.colorPrimary)
                                    .setIcon(R.drawable.ic_pass_reset)
                                    .setTitle("Password Recovery")
                                    .setMessage("Sent email to " + email)
                                    .setConfirmButtonText("Ok")
                                    .show();
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            new LovelyInfoDialog(LoginActivity.this) {
                                @Override
                                public LovelyInfoDialog setConfirmButtonText(String text) {
                                    findView(com.yarolegovich.lovelydialog.R.id.ld_btn_confirm).setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View view) {
                                            dismiss();
                                        }
                                    });
                                    return super.setConfirmButtonText(text);
                                }
                            }
                                    .setTopColorRes(R.color.colorAccent)
                                    .setIcon(R.drawable.ic_pass_reset)
                                    .setTitle("False")
                                    .setMessage("False to sent email to " + email)
                                    .setConfirmButtonText("Ok")
                                    .show();
                        }
                    });
        }

        /**
         * Luu thong tin user info cho nguoi dung dang nhap
         */
        void saveUserInfo() {
            FirebaseDatabase.getInstance().getReference().child("user/" + StaticConfig.UID).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    waitingDialog.dismiss();
                    HashMap hashUser = (HashMap) dataSnapshot.getValue();
                    User userInfo = new User();
                    userInfo.name = (String) hashUser.get("name");
                    userInfo.email = (String) hashUser.get("email");
                    userInfo.avata = (String) hashUser.get("avata");
                    SharedPreferenceHelper.getInstance(LoginActivity.this).saveUserInfo(userInfo);
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            });
        }

        /**
         * Khoi tao thong tin mac dinh cho tai khoan moi
         */
        void initNewUserInfo() {
            User newUser = new User();
            newUser.email = user.getEmail();
            newUser.name = user.getEmail().substring(0, user.getEmail().indexOf("@"));
            newUser.avata = StaticConfig.STR_DEFAULT_BASE64;
            FirebaseDatabase.getInstance().getReference().child("user/" + user.getUid()).setValue(newUser);
        }
    }
}
