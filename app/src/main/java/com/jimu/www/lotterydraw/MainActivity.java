package com.jimu.www.lotterydraw;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import com.google.gson.Gson;
import com.microsoft.projectoxford.vision.VisionServiceClient;
import com.microsoft.projectoxford.vision.VisionServiceRestClient;
import com.microsoft.projectoxford.vision.contract.Line;
import com.microsoft.projectoxford.vision.contract.OCR;
import com.microsoft.projectoxford.vision.contract.LanguageCodes;
import com.microsoft.projectoxford.vision.contract.Region;
import com.microsoft.projectoxford.vision.contract.Word;
import com.microsoft.projectoxford.vision.rest.VisionServiceException;
import com.qiniu.android.http.ResponseInfo;
import com.qiniu.android.storage.UpCompletionHandler;
import com.qiniu.android.storage.UploadManager;
import com.qiniu.util.Auth;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {
    private Button btnTakingPhoto;
    private Button btnQuery;
    private ImageView mImageView;
    private EditText editTextResult;
    private Uri mImageFileUri;

    private Auth qiuniuAuth;
    private VisionServiceClient visionServiceClient;

    private static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 100;
    private static final int CROP_IMAGE_ACTIVITY_REQUEST_CODE = 200;

    private static String qiniuAK = "RMMUumhhdruRJCgomWIyQzjsnEyvAytFIzkAXmwU";
    private static String qiniuSK = "ZMNhO0rbnyM9-hAsCO58xZVNm_PYYxXIE4Zf5Rxo";
    private static String qiniuBucket = "lottery";
    private static String qiniuDomain = "http://7xqpez.com1.z0.glb.clouddn.com/";
    private static String visionKey = "a9aa38e3e02a4c8cb87bf62b5b3411a1";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        initClickListener();

        qiuniuAuth = Auth.create(qiniuAK, qiniuSK);
        visionServiceClient = new VisionServiceRestClient(visionKey);
    }

    private void initView() {
        btnTakingPhoto = (Button) findViewById(R.id.btnTakingPhoto);
        btnQuery = (Button) findViewById(R.id.btnQuery);
        mImageView = (ImageView) findViewById(R.id.mImageView);
        editTextResult = (EditText) findViewById(R.id.editTextResult);
    }

    private void initClickListener() {
        btnTakingPhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dispatchCaptureIntent();
            }
        });

        btnQuery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                query();
            }
        });
    }

    private static Uri generateImageFileUri()
    {
        return Uri.fromFile(new File(Environment.getExternalStorageDirectory(),
                new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date())));
    }

    private void dispatchCaptureIntent() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        mImageFileUri = generateImageFileUri();
        intent.putExtra(MediaStore.EXTRA_OUTPUT, mImageFileUri);
        startActivityForResult(intent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode == RESULT_OK) {
            if (requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE) {
                cropPhoto(mImageFileUri);
            } else if (requestCode == CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
                mImageView.setImageURI(mImageFileUri);
            }
        }
    }

    public void cropPhoto(Uri uri) {
        Intent intent = new Intent("com.android.camera.action.CROP");
        intent.setDataAndType(uri, "image/*");
        intent.putExtra("crop", "true");
        intent.putExtra("aspectX", 1);
        intent.putExtra("aspectY", 1);
        intent.putExtra("outputX", 960);
        intent.putExtra("outputY", 960);
        intent.putExtra("return-data", false);
        mImageFileUri = generateImageFileUri();
        intent.putExtra(MediaStore.EXTRA_OUTPUT, mImageFileUri);

        startActivityForResult(intent, CROP_IMAGE_ACTIVITY_REQUEST_CODE);
    }


    private String process(String url) throws VisionServiceException, IOException {
        OCR ocr = visionServiceClient.recognizeText(url, LanguageCodes.AutoDetect, true);
        Gson gson = new Gson();
        String result = gson.toJson(ocr);
        Log.d("query", result);
        return result;
    }


    private class doRequest extends AsyncTask<String, String, String> {
        private Exception e = null;
        private String url = null;

        public doRequest(String url) {
            this.url = url;
        }

        @Override
        protected String doInBackground(String... args) {
            try {
                return process(url);
            } catch (Exception e) {
                this.e = e;    // Store error
            }

            return null;
        }

        @Override
        protected void onPostExecute(String data) {
            super.onPostExecute(data);
            // Display based on error existence

            if (e != null) {
                Log.e("query", e.getMessage());
                this.e = null;
            } else {
                Log.e("query", data);
                Gson gson = new Gson();
                OCR r = gson.fromJson(data, OCR.class);

                String result = "";
                for (Region reg : r.regions) {
                    for (Line line : reg.lines) {
                        for (Word word : line.words) {
                            result += word.text;
                        }
                        result += "\n";
                    }
                    result += "\n\n";
                }
                editTextResult.setText(result);
            }
        }
    }

    private void query() {

        String token = qiuniuAuth.uploadToken(qiniuBucket);
        UploadManager uploadManager = new UploadManager();
        uploadManager.put(mImageFileUri.getPath(), mImageFileUri.getLastPathSegment(), token,
            new UpCompletionHandler() {
                @Override
                public void complete(String key, ResponseInfo info, JSONObject response) {
                    Log.i("query", info.toString());
                    try {
                        new doRequest(qiniuDomain + mImageFileUri.getLastPathSegment()).execute();
                    } catch (Exception e) {
                        Log.e("query", e.getMessage());
                    }
                }
            }, null);
    }
}
