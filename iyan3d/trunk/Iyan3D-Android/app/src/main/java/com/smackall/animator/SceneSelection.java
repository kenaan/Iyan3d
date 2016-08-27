package com.smackall.animator;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

import com.android.vending.billing.IInAppBillingService;
import com.facebook.FacebookSdk;
import com.smackall.animator.Adapters.NoSpacingItemDecoreation;
import com.smackall.animator.Adapters.SceneSelectionAdapter;
import com.smackall.animator.Analytics.HitScreens;
import com.smackall.animator.GCM.QuickstartPreferences;
import com.smackall.animator.GCM.RegistrationIntentService;
import com.smackall.animator.Helper.BadgeView;
import com.smackall.animator.Helper.CheckLicense;
import com.smackall.animator.Helper.Constants;
import com.smackall.animator.Helper.CreditsManager;
import com.smackall.animator.Helper.DatabaseHelper;
import com.smackall.animator.Helper.DescriptionManager;
import com.smackall.animator.Helper.Events;
import com.smackall.animator.Helper.FileHelper;
import com.smackall.animator.Helper.FollowApp;
import com.smackall.animator.Helper.FullScreen;
import com.smackall.animator.Helper.PathManager;
import com.smackall.animator.Helper.RestoreBackUp;
import com.smackall.animator.Helper.SceneDB;
import com.smackall.animator.Helper.SharedPreferenceManager;
import com.smackall.animator.Helper.UIHelper;
import com.smackall.animator.OverlayDialogs.HelpDialogs;
import com.smackall.animator.UserOnBoarding.UserOnBoarding;
import com.twitter.sdk.android.core.TwitterAuthConfig;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SceneSelection extends AppCompatActivity implements ServiceConnection {

    public DatabaseHelper db = new DatabaseHelper();
    public SceneSelectionAdapter sceneAdapter;
    public FollowApp followApp;
    public Login login;
    public NewsFeed newsFeed;
    public CloudRenderingProgress cloudRenderingProgress;
    public UserDetails userDetails;
    public SharedPreferenceManager sharedPreferenceManager = new SharedPreferenceManager();
    public HelpDialogs helpDialogs = new HelpDialogs();
    public DescriptionManager descriptionManager = new DescriptionManager();
    public CreditsManager creditsManager;
    public Settings settings;
    public RestoreBackUp restoreBackUp;
    InfoPopUp infoPopUp = new InfoPopUp();
    IInAppBillingService mService;
    boolean fromLoadingView = true;
    boolean isFirstTimeForToolTip = false;
    BadgeView badgeView = null;
    private BroadcastReceiver mRegistrationBroadcastReceiver;
    private boolean isReceiverRegistered;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FullScreen.HideStatusBar(this);
        setContentView(R.layout.activity_scene_selection);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        HitScreens.SceneSelectionView(SceneSelection.this);
        Constants.currentActivity = 0;
        restoreBackUp = new RestoreBackUp(this);
        initGridView();
        initClass();
        if (!CheckLicense.checkLicense(SceneSelection.this)) {
            Events.licenseVerificationFailed(SceneSelection.this);
            android.app.AlertDialog.Builder informDialog = new android.app.AlertDialog.Builder(SceneSelection.this);
            informDialog
                    .setMessage(R.string.license_verification_failed)
                    .setCancelable(false)
                    .setPositiveButton(R.string.exit, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dealloc();
                        }
                    });
            informDialog.create();
            try {
                informDialog.show();
            } catch (WindowManager.BadTokenException e) {
                e.printStackTrace();
            }
        }

        mRegistrationBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                SharedPreferences sharedPreferences =
                        PreferenceManager.getDefaultSharedPreferences(context);
                boolean sentToken = sharedPreferences
                        .getBoolean(QuickstartPreferences.SENT_TOKEN_TO_SERVER, false);
            }
        };
        Bundle data = getIntent().getExtras();
        if (data != null)
            fromLoadingView = data.getBoolean("fromLoading");
        if (data != null && data.getBoolean("isNotification")) {
            findViewById(R.id.login_btn).performClick();
        }
        if (data != null && data.getBoolean("hasExtraForOpenWith")) {
            handleOpenWith(getIntent());
        } else {
            try {
                Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
                serviceIntent.setPackage("com.android.vending");
                SceneSelection.this.bindService(serviceIntent, SceneSelection.this, Context.BIND_AUTO_CREATE);
            } catch (RuntimeException ignored) {
            }
        }


        registerReceiver();

        if (Constants.checkPlayServices(SceneSelection.this)) {
            // Start IntentService to register this application with GCM.
            Intent intent = new Intent(this, RegistrationIntentService.class);
            startService(intent);
        }

        float space = Constants.getFreeSpace();
        if (space < 50.0) {
            UIHelper.informDialog(SceneSelection.this, getString(R.string.phone_storage_low_need_50mb), true);
        }

        Constants.isFirstTimeUser = (sharedPreferenceManager.getInt(SceneSelection.this, "firstTimeUser") == 0);

        if (sharedPreferenceManager.getInt(this, "firstTimeUser") == 0) {
            Intent i = new Intent(SceneSelection.this, UserOnBoarding.class);
            i.putExtra("firstTimeUser", 1);
            startActivity(i);
        }
    }

    @Override
    protected void onResume() {
        sceneAdapter.notifyDataSetChanged();
        super.onResume();
    }

    @Override
    protected void onPause() {
        overridePendingTransition(0, 0);
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Bundle a = intent.getExtras();
        if (a != null && a.getBoolean("hasExtraForOpenWith")) {
            handleOpenWith(intent);
        } else if (a != null && a.getInt("userOnBoardClosed") == 1) {
            isFirstTimeForToolTip = true;
            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (sharedPreferenceManager.getInt(SceneSelection.this, "firstTimeUserForSceneSelection") == 0) {
                        sharedPreferenceManager.setData(SceneSelection.this, "firstTimeUserForSceneSelection", 1);
                        showHelp(null);
                    }
                }
            }, 100);
        } else {
            findViewById(R.id.login_btn).performClick();
        }
    }

    public void handleOpenWith(Intent intent) {

        String path = intent.getStringExtra("i3dPath");
        if (path != null && !path.toLowerCase().equals("null")) {
            String ext = FileHelper.getFileExt(path);
            switch (ext) {
                case "png":
                    FileHelper.manageImageFile(path, SceneSelection.this);
                    UIHelper.informDialog(SceneSelection.this, getString(R.string.img_import_sucessfully), true);
                    break;
                case "obj":
                    UIHelper.informDialog(SceneSelection.this, getString(R.string.model_import_successfully), true);
                    break;
                case "ttf":
                case "otf":
                    UIHelper.informDialog(SceneSelection.this, getString(R.string.font_import_successfully), true);
                    break;
                case "i3d":
                    restoreBackUp.restoreI3D(path);
                    break;
            }
        }
    }

    private void registerReceiver() {
        if (!isReceiverRegistered) {
            LocalBroadcastManager.getInstance(this).registerReceiver(mRegistrationBroadcastReceiver,
                    new IntentFilter(QuickstartPreferences.REGISTRATION_COMPLETE));
            isReceiverRegistered = true;
        }
    }

    private void initClass() {
        followApp = new FollowApp(SceneSelection.this);
        login = new Login(SceneSelection.this);
        login.initAllSignSdk();
        newsFeed = new NewsFeed(SceneSelection.this, sharedPreferenceManager, db);
        newsFeed.syncNewsFeed(true);
        cloudRenderingProgress = new CloudRenderingProgress(SceneSelection.this, db, mService);
        userDetails = new UserDetails(SceneSelection.this, sharedPreferenceManager);
        creditsManager = new CreditsManager(SceneSelection.this);
        settings = new Settings(SceneSelection.this, sharedPreferenceManager, mService);
    }

    private void initGridView() {
        RecyclerView gridview = (RecyclerView) findViewById(R.id.gridView);

        if (gridview != null) {
            gridview.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (helpDialogs != null && sharedPreferenceManager.getInt(SceneSelection.this, "firstTimeUserForSceneSelection") == 1)
                        helpDialogs.dismissTips(false, null);
                    return false;
                }
            });
        }

        sceneAdapter = new SceneSelectionAdapter(this, db, gridview);

        if (gridview != null) {
            gridview.setItemAnimator(new DefaultItemAnimator());
            GridLayoutManager gridLayoutManager = new GridLayoutManager(SceneSelection.this, 4);
            gridview.setLayoutManager(gridLayoutManager);
            gridview.addItemDecoration(new NoSpacingItemDecoreation());
            gridview.setAdapter(sceneAdapter);
        }
    }

    public void addNewScene(View view) {
        helpDialogs.dismissTips(false, null);
        if (view != null) Events.createNewSceneInTopLeft(SceneSelection.this);
        else Events.createNewSceneInCollectionView(SceneSelection.this);

        String date = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(new Date());
        List<SceneDB> sceneDBs = db.getAllScenes();
        String prefix = getResources().getString(R.string.myscene);
        String sceneName = (sceneDBs == null || sceneDBs.size() <= 0) ? prefix + " " + 1 : prefix + " " + Integer.toString(sceneDBs.get(db.getSceneCount() - 1).getID() + 1);
        FileHelper.copySingleAssetFile(SceneSelection.this, "scenebg.jpg", PathManager.LocalScenesFolder,FileHelper.md5(sceneName)+".png");
        db.addNewScene(new SceneDB(sceneName, FileHelper.md5(sceneName), date));
        this.sceneAdapter.sceneDBs = db.getAllScenes();
        this.sceneAdapter.notifyDataSetChanged();
        RecyclerView gridview = (RecyclerView) findViewById(R.id.gridView);
        if (gridview != null)
            gridview.scrollToPosition((sceneAdapter != null && sceneAdapter.sceneDBs != null) ? this.sceneAdapter.sceneDBs.size() : 0);
    }

    public void showRenameDialog(final int position) {
        final AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setMessage(getString(R.string.enter_scene_name));
        final EditText input = new EditText(this);
        input.setHint(R.string.scene_name);
        alert.setView(input);
        alert.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                if (input.getText().toString().trim().length() != 0) {
                    if (FileHelper.isItHaveSpecialChar(input.getText().toString().trim())) {
                        UIHelper.informDialog(SceneSelection.this, getString(R.string.special_char_in_scene), true);
                        return;
                    }
                    List<SceneDB> sceneDBs = db.getAllSceneDetailsWithSearch(input.getText().toString());
                    if (sceneDBs.size() != 0) {
                        UIHelper.informDialog(SceneSelection.this, getString(R.string.scene_already_exits), true);
                    } else {
                        renameScene(input.getText().toString().trim(), position);
                    }
                } else {
                    if (dialog != null)
                        dialog.dismiss();
                    UIHelper.informDialog(SceneSelection.this, getString(R.string.scene_name_cannot_empty), true);
                }
            }
        });

        alert.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                FullScreen.HideStatusBar(SceneSelection.this);
                if (dialog != null)
                    dialog.dismiss();
            }
        });
        AlertDialog alertDialog = alert.create();
        alertDialog.show();
    }

    private void renameScene(String name, int position) {
        List<SceneDB> sceneDBs = db.getAllScenes();
        if (sceneDBs == null || sceneDBs.size() == 0) return;
        db.updateSceneDetails(new SceneDB(sceneDBs.get(position).getID(), name, sceneDBs.get(position).getImage(), sceneDBs.get(position).getTime()));
        this.sceneAdapter.sceneDBs = db.getAllScenes();
        this.sceneAdapter.notifyDataSetChanged();
    }

    public void deleteScene(int position) {
        List<SceneDB> sceneDBs = db.getAllScenes();
        if (sceneDBs == null || sceneDBs.size() == 0) return;
        File image = new File(PathManager.LocalScenesFolder + "/" + sceneDBs.get(position).getImage() + ".png");
        File projectFile = new File(PathManager.LocalProjectFolder + "/" + sceneDBs.get(position).getImage() + ".sgb");
        File backUpFile = new File(PathManager.LocalProjectFolder + "/" + sceneDBs.get(position).getImage() + ".i3d");
        if (projectFile.exists())
            projectFile.delete();
        if (image.exists())
            image.delete();
        if (backUpFile.exists())
            backUpFile.delete();
        db.deleteScene(sceneDBs.get(position).getName());
        this.sceneAdapter.sceneDBs = db.getAllScenes();
        this.sceneAdapter.notifyDataSetChanged();
    }

    public void cloneScene(int position) {
        String date = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(new Date());
        List<SceneDB> sceneDBs = db.getAllScenes();
        if (sceneDBs == null || sceneDBs.size() == 0) return;
        String prefix = getResources().getString(R.string.myscene);
        String sceneName = (sceneDBs.size() <= 0) ? prefix + " " + 1 : prefix + " " + Integer.toString(sceneDBs.get(db.getSceneCount() - 1).getID() + 1);
        File thumpnailFrom = new File(PathManager.LocalScenesFolder + "/" + sceneDBs.get(position).getImage() + ".png");
        File thumpnailTo = new File(PathManager.LocalScenesFolder + "/" + FileHelper.md5(sceneName) + ".png");

        File projectFrom = new File(PathManager.LocalProjectFolder + "/" + sceneDBs.get(position).getImage() + ".sgb");
        File projectTo = new File(PathManager.LocalProjectFolder + "/" + FileHelper.md5(sceneName) + ".sgb");
        if (thumpnailFrom.exists())
            FileHelper.copy(thumpnailFrom, thumpnailTo);
        if (projectFrom.exists())
            FileHelper.copy(projectFrom, projectTo);
        db.addNewScene(new SceneDB(sceneName, FileHelper.md5(sceneName), date));
        this.sceneAdapter.sceneDBs = db.getAllScenes();
        this.sceneAdapter.notifyDataSetChanged();
    }

    public void backUp(String fileName) {
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(PathManager.LocalProjectFolder + "/" + fileName)));
        shareIntent.setType("*/*");
        startActivity(Intent.createChooser(shareIntent, getString(R.string.backup_to)));
    }

    public void loadScene(int position) {
        helpDialogs.dismissTips(false, null);
        if (fromLoadingView) {
            Events.sceneOpenedFirstTime(SceneSelection.this, (sharedPreferenceManager.getInt(SceneSelection.this, "firstTimeUser") == 0) ? "YES" : "NO");
            if ((sharedPreferenceManager.getInt(SceneSelection.this, "firstTimeUser") == 0)) {
                sharedPreferenceManager.setData(SceneSelection.this, "lastAnimationJsonUpdatedTime", System.currentTimeMillis());
                sharedPreferenceManager.setData(SceneSelection.this, "lastAssetJsonUpdatedTime", System.currentTimeMillis());
            }
            sharedPreferenceManager.setData(SceneSelection.this, "firstTimeUser", 1);
        }

        Intent editorScene = new Intent(SceneSelection.this, EditorView.class);
        editorScene.putExtra("scenePosition", position);
        editorScene.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        editorScene.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        overridePendingTransition(0, 0);
        startActivity(editorScene);
        dealloc();
    }

    public void openSceneProps(View v) {
        helpDialogs.dismissTips(false, null);
        if (infoPopUp != null)
            infoPopUp.infoPopUpMenu(SceneSelection.this, v);
    }

    public void showNewsFeed(View v) {
        newsFeed.showNewsFeed(null, v);
        clearNewsFeedBadge();
        int lastIdForCount = sharedPreferenceManager.getInt(this, "newsFeedCountLastId");
        if (db.getAllFeeds(String.valueOf(lastIdForCount)) != null && db.getAllFeeds(String.valueOf(lastIdForCount)).size() > 0) {
            sharedPreferenceManager.setData(this, "newsFeedCountLastId", db.getAllFeeds(String.valueOf(lastIdForCount)).get(0).getId());
        }
    }

    public void showLogIn(View v) {
        helpDialogs.dismissTips(false, null);
        userDetails.updateUserDetails();
        if (userDetails.signInType > 0)
            cloudRenderingProgress.showCloudRenderingProgress(v, null);
        else
            login.showLogin(v, null);
    }

    public void showHelp(View view) {
        descriptionManager.addSceneSelectionDescriptions(this);
        helpDialogs.dismissTips(false, null);
        helpDialogs.showPop(SceneSelection.this);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1002) {
            cloudRenderingProgress.purchaseResult(requestCode, resultCode, data);
        } else if (requestCode == TwitterAuthConfig.DEFAULT_AUTH_REQUEST_CODE) {
            login.twitterButton.onActivityResult(requestCode, resultCode, data);
        } else if (FacebookSdk.isFacebookRequestCode(requestCode)) {
            login.callbackManager.onActivityResult(requestCode, resultCode, data);
        } else if (requestCode == Constants.GOOGLE_SIGNIN_REQUESTCODE) {
            login.googleSignInActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        moveTaskToBack(true);
    }


    private void dealloc() {
        try {
            unbindService(SceneSelection.this);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }

        db = null;
        sceneAdapter = null;
        infoPopUp = null;

        this.finish();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mService = IInAppBillingService.Stub.asInterface(service);
        settings.mService = mService;
        cloudRenderingProgress.mService = mService;
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        mService = null;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        FullScreen.HideStatusBar(SceneSelection.this);
        if (isFirstTimeForToolTip) {
            isFirstTimeForToolTip = false;
        }
        super.onWindowFocusChanged(hasFocus);
    }

    public void showNewsFeedBadge(String msg) {
        if (badgeView == null)
            badgeView = new BadgeView(this, findViewById(R.id.newsFeed_btn));
        badgeView.setText(msg);
        badgeView.setBadgePosition(BadgeView.POSITION_TOP_RIGHT);
        badgeView.show();
    }

    public void clearNewsFeedBadge() {
        if (badgeView != null && badgeView.isShown()) {
            badgeView.hide();
            badgeView = null;
        }
    }
}
