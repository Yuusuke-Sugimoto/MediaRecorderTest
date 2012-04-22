package jp.ddo.kingdragon;

import android.app.Activity;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MediaRecorderTestActivity extends Activity implements SurfaceHolder.Callback {
    private SurfaceView preview;
    private MediaRecorder recorder;
    Camera mCamera;
    Camera.Parameters params;

    private File baseDir;
    private File destFile;
    private boolean capturing;
    private boolean launched;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.main);

        capturing = false;
        launched = false;

        // 保存用ディレクトリの作成
        baseDir = new File(Environment.getExternalStorageDirectory(), "tottepost");
        try {
            if(!baseDir.exists() && !baseDir.mkdirs()) {
                Toast.makeText(MediaRecorderTestActivity.this, "", Toast.LENGTH_SHORT).show();

                finish();
            }
        }
        catch(Exception e) {
            Toast.makeText(MediaRecorderTestActivity.this, "", Toast.LENGTH_SHORT).show();
            e.printStackTrace();

            finish();
        }

        preview = (SurfaceView)findViewById(R.id.preview);
        preview.getHolder().addCallback(this);
        preview.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    @Override
    public void onResume() {
        super.onResume();

        surfaceChanged(preview.getHolder(), 0, 0, 0);
    }

    @Override
    public void onPause() {
        super.onPause();

        if(mCamera != null) {
            // カメラのリソースを利用中であれば解放する
            mCamera.stopPreview();
            mCamera.release();
            params = null;
            mCamera = null;
        }
        launched = false;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if(mCamera != null) {
            mCamera.stopPreview();
        }
        else {
            mCamera = Camera.open();
            params = mCamera.getParameters();
        }

        if(!launched) {
            // 各種パラメータの設定
            // 保存する画像サイズを決定
            List<Camera.Size> pictureSizes = params.getSupportedPictureSizes();
            Camera.Size picSize = pictureSizes.get(0);
            for(int i = 1; i < pictureSizes.size(); i++) {
                Camera.Size temp = pictureSizes.get(i);
                if(picSize.width * picSize.height > 2048 * 1232
                   || picSize.width * picSize.height < temp.width * temp.height) {
                    // 2048 x 1232以下で一番大きな画像サイズを選択
                    picSize = temp;
                }
            }
            params.setPictureSize(picSize.width, picSize.height);

            // 画像サイズを元にプレビューサイズを決定
            List<Camera.Size> previewSizes = params.getSupportedPreviewSizes();
            Camera.Size preSize = previewSizes.get(0);
            for(int i = 1; i < previewSizes.size(); i++) {
                Camera.Size temp = previewSizes.get(i);
                if(preSize.width * preSize.height < temp.width * temp.height) {
                    if(Math.abs((double)picSize.width / (double)picSize.height - (double)preSize.width / (double)preSize.height)
                       >= Math.abs((double)picSize.width / (double)picSize.height - (double)temp.width / (double)temp.height)) {
                        // 一番保存サイズの比に近くてかつ一番大きなプレビューサイズを選択
                        preSize = temp;
                    }
                }
            }
            params.setPreviewSize(preSize.width, preSize.height);

            // プレビューサイズを元にSurfaceViewのサイズを決定
            WindowManager manager = (WindowManager)getSystemService(WINDOW_SERVICE);
            Display mDisplay = manager.getDefaultDisplay();
            ViewGroup.LayoutParams lParams = preview.getLayoutParams();
            lParams.width  = mDisplay.getWidth();
            lParams.height = mDisplay.getHeight();
            if((double)preSize.width / (double)preSize.height
                > (double)mDisplay.getWidth() / (double)mDisplay.getHeight()) {
                // 横の長さに合わせる
                lParams.height = preSize.height * mDisplay.getWidth() / preSize.width;
            }
            else if((double)preSize.width / (double)preSize.height
                    < (double)mDisplay.getWidth() / (double)mDisplay.getHeight()) {
                // 縦の長さに合わせる
                lParams.width  = preSize.width * mDisplay.getHeight() / preSize.height;
            }
            preview.setLayoutParams(lParams);
            params.setRotation(90);
            mCamera.setParameters(params);

            launched = true;
        }

        if(!capturing) {
            try {
                mCamera.setPreviewDisplay(preview.getHolder());
                mCamera.startPreview();
            }
            catch(Exception e) {
                Log.e("surfaceChanged", e.getMessage(), e);

                finish();
            }
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {}

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if(event.getAction() == MotionEvent.ACTION_DOWN) {
            if(capturing) {
                recorder.stop();
                recorder.reset();
                recorder.release();
                mCamera.lock();
                capturing = false;
                launched = false;
                surfaceChanged(preview.getHolder(), 0, 0, 0);
            }
            else {
                initRecorder();
                recorder.start();
                capturing = true;
            }
        }

        return(true);
    }

    public void initRecorder() {
        /***
         * 動画を撮影する
         * 参考:Camera | Android Developers
         *      http://developer.android.com/guide/topics/media/camera.html
         *
         *      MediaRecorder | Android Developers
         *      http://developer.android.com/reference/android/media/MediaRecorder.html
         *
         *      MediaRecorderの解像度設定: とくぼーのブログ
         *      http://tokubo.cocolog-nifty.com/blog/2011/07/mediarecorder-4.html
         */
        mCamera.unlock();

        recorder = new MediaRecorder();
        recorder.setCamera(mCamera);
        recorder.setVideoSource(MediaRecorder.VideoSource.DEFAULT);
        recorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);

        // プレビューサイズを決定
        List<Camera.Size> previewSizes = params.getSupportedPreviewSizes();
        Camera.Size preSize = previewSizes.get(0);
        for(int i = 1; i < previewSizes.size(); i++) {
            Camera.Size temp = previewSizes.get(i);
            if(preSize.width * preSize.height < temp.width * temp.height) {
                // 一番大きなプレビューサイズを選択
                preSize = temp;
            }
        }

        // プレビューサイズを元にSurfaceViewのサイズを決定
        WindowManager manager = (WindowManager)getSystemService(WINDOW_SERVICE);
        Display mDisplay = manager.getDefaultDisplay();
        ViewGroup.LayoutParams lParams = preview.getLayoutParams();
        lParams.width  = mDisplay.getWidth();
        lParams.height = mDisplay.getHeight();
        if((double)preSize.width / (double)preSize.height
            > (double)mDisplay.getWidth() / (double)mDisplay.getHeight()) {
            // 横の長さに合わせる
            lParams.height = preSize.height * mDisplay.getWidth() / preSize.width;
        }
        else if((double)preSize.width / (double)preSize.height
                < (double)mDisplay.getWidth() / (double)mDisplay.getHeight()) {
            // 縦の長さに合わせる
            lParams.width  = preSize.width * mDisplay.getHeight() / preSize.height;
        }
        preview.setLayoutParams(lParams);

        CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
        profile.videoFrameWidth  = preSize.width;
        profile.videoFrameHeight = preSize.height;
        recorder.setProfile(profile);

        // ファイル名を生成
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddkkmmss");
        String fileName = "tottepost_" + dateFormat.format(new Date()) + ".mp4";
        destFile = new File(baseDir, fileName);
        recorder.setOutputFile(destFile.getAbsolutePath());

        recorder.setPreviewDisplay(preview.getHolder().getSurface());
        try {
            recorder.prepare();
        }
        catch(Exception e) {
            Log.e("surfaceChanged", e.getMessage(), e);

            finish();
        }
    }
}