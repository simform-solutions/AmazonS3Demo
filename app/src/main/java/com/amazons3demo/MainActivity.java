package com.amazons3demo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.Builder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.tbruyelle.rxpermissions2.RxPermissions;

import java.io.File;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static com.amazons3demo.Constants.BUCKET_NAME;
import static com.amazons3demo.Constants.endPoint;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    static final int REQUEST_VIDEO_CAPTURE = 1;
    static final int REQUEST_VIDEO_GALLERY = 2;

    private Button buttonChoose;
    private Button buttonUpload;
    private Button buttonCamera;
    private TextView textView;

    private TextView textViewResponse;
    private AmazonS3Client amazonS3;
    private CognitoCachingCredentialsProvider sCredProvider;
    private String TAG = "TAG";

    private String path;
    private NotificationManager mNotifyManager;
    private Builder mBuilder;
    private String key;
    private int percentage;
    private RxPermissions rxPermissions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        init();

    }

    private void init() {
        buttonChoose = (Button) findViewById(R.id.buttonChoose);
        buttonCamera = (Button) findViewById(R.id.buttonCamera);
        buttonUpload = (Button) findViewById(R.id.buttonUpload);

        textView = (TextView) findViewById(R.id.textView);
        textViewResponse = (TextView) findViewById(R.id.textViewResponse);

        buttonChoose.setOnClickListener(this);
        buttonUpload.setOnClickListener(this);
        buttonCamera.setOnClickListener(this);

        rxPermissions = new RxPermissions(this);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
        Date date = new Date();
        key = sdf.format(date);


        /**
         *  WITH ACCESSKEY AND SECRETKEY LOGIN
         * */
//        amazonS3 = new AmazonS3Client(new BasicAWSCredentials(accessKeyId, secretKeyId));


        /**
         *  WITH COGNITO LOGIN
         * */
        amazonS3 = new AmazonS3Client(getCredProvider());
    }


    private CognitoCachingCredentialsProvider getCredProvider() {
        if (sCredProvider == null) {
            sCredProvider = new CognitoCachingCredentialsProvider(
                    getApplicationContext(),
                    Constants.COGNITO_POOL_ID,
                    Regions.fromName(Constants.COGNITO_POOL_REGION));
        }
        return sCredProvider;
    }


    private void dispatchTakeVideoIntent() {
        Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);  // SET  ACTION_IMAGE_CAPTURE  IF YOU WANT TO UPLOAD IMAGE
        if (takeVideoIntent.resolveActivity(getPackageManager()) != null) {
            takeVideoIntent.putExtra("android.intent.extra.durationLimit", 5);
            startActivityForResult(takeVideoIntent, REQUEST_VIDEO_CAPTURE);
        }
    }

    private void chooseVideo() {
        Intent intent = new Intent();
        if (Build.VERSION.SDK_INT >= 19) {
            // For Android KitKat, we use a different intent to ensure
            // we can
            // get the file path from the returned intent URI
            intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
            intent.setType("video/*");
            // YOU CAN SET FILE IF YOU WANT TO UPLOAD FILE
            //intent.setType("file/*");
        } else {
            intent.setAction(Intent.ACTION_GET_CONTENT);
            intent.setType("video/*");
            // YOU CAN SET FILE IF YOU WANT TO UPLOAD FILE
            //intent.setType("file/*");
        }

        startActivityForResult(intent, REQUEST_VIDEO_GALLERY);
    }

    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    @SuppressLint("NewApi")
    private String getPath(Uri uri) throws URISyntaxException {
        final boolean needToCheckUri = Build.VERSION.SDK_INT >= 19;
        String selection = null;
        String[] selectionArgs = null;
        // Uri is different in versions after KITKAT (Android 4.4), we need to
        // deal with different Uris.
        if (needToCheckUri && DocumentsContract.isDocumentUri(getApplicationContext(), uri)) {
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                return Environment.getExternalStorageDirectory() + "/" + split[1];
            } else if (isDownloadsDocument(uri)) {
                final String id = DocumentsContract.getDocumentId(uri);
                uri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
            } else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];
                if ("image".equals(type)) {
                    uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }
                selection = "_id=?";
                selectionArgs = new String[]{
                        split[1]
                };
            }
        }
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            String[] projection = {
                    MediaStore.Images.Media.DATA
            };
            Cursor cursor = null;
            try {
                cursor = getContentResolver()
                        .query(uri, projection, selection, selectionArgs, null);
                int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                if (cursor.moveToFirst()) {
                    return cursor.getString(column_index);
                }
            } catch (Exception e) {
            }
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == REQUEST_VIDEO_CAPTURE && resultCode == RESULT_OK) {
            Uri videoUri = data.getData();
            try {
                path = getPath(videoUri);
                textView.setText(path);
            } catch (URISyntaxException e) {
                e.printStackTrace();
                Toast.makeText(this, "Unable to get the file from the given URI.  See error log for details", Toast.LENGTH_LONG).show();
                Log.e("TAG", "Unable to upload file from the given uri", e);
            }
        } else if (requestCode == REQUEST_VIDEO_GALLERY && resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();
            try {
                path = getPath(uri);
                textView.setText(path);
            } catch (URISyntaxException e) {
                Toast.makeText(this, "Unable to get the file from the given URI.  See error log for details", Toast.LENGTH_LONG).show();
                Log.e("TAG", "Unable to upload file from the given uri", e);
            }
        }
    }

    private void beginUpload() {

        TransferUtility transferUtility = new TransferUtility(amazonS3, getApplicationContext());
        if (path == null) {
            Toast.makeText(this, "Could not find the filepath of the selected file",
                    Toast.LENGTH_LONG).show();
            return;
        }
        File file = new File(path);
        amazonS3.setEndpoint(endPoint);


//        TransferObserver observer = transferUtility.upload(BUCKET_NAME, key, file);

        /**
         * PROVIDE ACCESS TO READ PUBLIC
         * */
        TransferObserver observer = transferUtility.upload(BUCKET_NAME, key, file, CannedAccessControlList.PublicRead);

        observer.setTransferListener(new UploadListener());

        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
        mNotifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mBuilder = new NotificationCompat.Builder(MainActivity.this);
        mBuilder.setContentIntent(pendingIntent);

    }

    @Override
    public void onClick(View v) {
        if (v == buttonChoose) {
            openGallery();
        }
        if (v == buttonUpload) {
            beginUpload();
            mBuilder.setProgress(100, 0, false);
        }
        if (v == buttonCamera) {
            openCamera();
        }
    }

    private class UploadListener implements TransferListener {
        @Override
        public void onError(int id, Exception e) {
            Log.e(TAG, "Error during upload: " + id, e);
            setNotificationForError(id, e);
        }

        @Override
        public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
            Log.d(TAG, String.format("onProgressChanged: %d, total: %d, current: %d", id, bytesTotal, bytesCurrent));
            setNotification(id, bytesCurrent, bytesTotal);
        }

        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void onStateChanged(int id, TransferState newState) {
            if (!isNetworkConnected(getApplicationContext())) {
                mBuilder.setContentTitle("Uploading " + (percentage + "%"))
                        .setContentText(key) // SET VIDEO TITLE
                        .setSmallIcon(R.mipmap.ic_launcher_round);
                mBuilder.setOngoing(false);
                mNotifyManager.notify(id, mBuilder.build());
            } else {
                mNotifyManager.cancel("", id);
            }
            Log.d(TAG, "onStateChanged: " + id + ", " + newState);
        }
    }

    private void setNotificationForError(int id, Exception e) {
        mBuilder.setContentTitle("Error")
                .setContentText(key) // SET VIDEO TITLE
                .setSmallIcon(R.mipmap.ic_launcher_round);
        mBuilder.setOngoing(false);
        mNotifyManager.notify(id, mBuilder.build());
    }

    private void setNotification(int id, long bytesCurrent, long bytesTotal) {

        percentage = (int) ((bytesCurrent * 100) / bytesTotal);

        mBuilder.setContentTitle("Uploading " + (percentage + "%"))
                .setContentText(key) // SET VIDEO TITLE
                .setSmallIcon(R.mipmap.ic_launcher_round);

        mBuilder.setOngoing(true);
        Log.d(TAG, String.format("percentage value %d", percentage));
        mNotifyManager.notify(id, mBuilder.build());

        mBuilder.setProgress(100, percentage, false);
        mNotifyManager.notify(id, mBuilder.build());

        if (percentage == 100) {

            mBuilder.setContentTitle("Upload completed")
                    .setContentText(key)  // SET VIDEO TITLE
                    .setSmallIcon(R.mipmap.ic_launcher_round);
            mBuilder.setOngoing(false);
            mBuilder.setProgress(0, 0, false);
            mNotifyManager.notify(id, mBuilder.build());
            /**
             * FOR GET UPLOADED FILE URL
             * */
            //String resourceUrl = amazonS3.getResourceUrl(BUCKET_NAME, key);
            //textViewResponse.setText(resourceUrl);
        }
    }

    public static boolean isNetworkConnected(Context context) {
        ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    private void openGallery() {
        rxPermissions.request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .subscribe(granted -> {
                    if (granted) { // Always true pre-M
                        chooseVideo();
                    } else {
                        Toast.makeText(this, "Permission Not Granted", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void openCamera() {
        rxPermissions.request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .subscribe(granted -> {
                    if (granted) { // Always true pre-M
                        dispatchTakeVideoIntent();
                    } else {
                        Toast.makeText(this, "Permission Not Granted", Toast.LENGTH_LONG).show();
                    }
                });
    }
}