package disono.com.webmons.javacv_face_recognition;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.hardware.Camera;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_objdetect;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import static org.bytedeco.javacpp.helper.opencv_objdetect.cvHaarDetectObjects;
import static org.bytedeco.javacpp.opencv_core.IPL_DEPTH_8U;
import static org.bytedeco.javacpp.opencv_core.cvClearMemStorage;
import static org.bytedeco.javacpp.opencv_core.cvGetSeqElem;
import static org.bytedeco.javacpp.opencv_core.cvLoad;
import static org.bytedeco.javacpp.opencv_objdetect.CV_HAAR_DO_ROUGH_SEARCH;
import static org.bytedeco.javacpp.opencv_objdetect.CV_HAAR_FIND_BIGGEST_OBJECT;

/**
 * Author: Archie, Disono (disono.apd@gmail.com / webmonsph@gmail.com)
 * Website: www.webmons.com
 * License: Apache 2.0
 * Created at: 2016-08-09 12:08 PM
 */

/**
 * This is an example how to detect face in a video file with javacv
 * @author Vincent He (chinadragon0515@gmail.com)
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity::Activity";
    Context ctx;

    private FrameLayout layout;
    private FaceView faceView;
    private Preview mPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ctx = this.getApplication().getApplicationContext();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Create our Preview view and set it as the content of our activity.
        try {
            layout = new FrameLayout(this);

            faceView = new FaceView(this);
            mPreview = new Preview(this, faceView);

            layout.addView(mPreview);
            layout.addView(faceView);

            setContentView(layout);
        } catch (IOException e) {
            e.printStackTrace();
            new AlertDialog.Builder(this).setMessage(e.getMessage()).create().show();
        }
    }

    class FaceView extends View implements Camera.PreviewCallback {
        public static final int SUBSAMPLING_FACTOR = 4;

        private opencv_core.IplImage grayImage;
        private opencv_objdetect.CvHaarClassifierCascade classifier;
        private opencv_core.CvMemStorage storage;
        private opencv_core.CvSeq faces;

        public FaceView(MainActivity context) throws IOException {
            super(context);

            // Load the classifier file from Java resources.
            File classifierFile = Loader.extractResource(getClass(),
                    "/xml/haarcascade_frontalface_alt.xml",
                    context.getCacheDir(), "classifier", ".xml");
            if (classifierFile == null || classifierFile.length() <= 0) {
                throw new IOException("Could not extract the classifier file from Java resource.");
            }

            // Preload the opencv_objdetect module to work around a known bug.
            Loader.load(opencv_objdetect.class);
            classifier = new opencv_objdetect.CvHaarClassifierCascade(cvLoad(classifierFile.getAbsolutePath()));
            classifierFile.delete();
            if (classifier.isNull()) {
                throw new IOException("Could not load the classifier file.");
            }
            storage = opencv_core.CvMemStorage.create();
        }

        public void onPreviewFrame(final byte[] data, final Camera camera) {
            try {
                Camera.Size size = camera.getParameters().getPreviewSize();
                processImage(data, size.width, size.height);
                camera.addCallbackBuffer(data);
            } catch (RuntimeException e) {
                // The camera has probably just been released, ignore.
            }
        }

        protected void processImage(byte[] data, int width, int height) {
            // First, downsample our image and convert it into a grayscale IplImage
            int f = SUBSAMPLING_FACTOR;
            if (grayImage == null || grayImage.width() != width / f || grayImage.height() != height / f) {
                grayImage = opencv_core.IplImage.create(width / f, height / f, IPL_DEPTH_8U, 1);
            }
            int imageWidth = grayImage.width();
            int imageHeight = grayImage.height();
            int dataStride = f * width;
            int imageStride = grayImage.widthStep();
            ByteBuffer imageBuffer = grayImage.getByteBuffer();
            for (int y = 0; y < imageHeight; y++) {
                int dataLine = y * dataStride;
                int imageLine = y * imageStride;
                for (int x = 0; x < imageWidth; x++) {
                    imageBuffer.put(imageLine + x, data[dataLine + f * x]);
                }
            }

            cvClearMemStorage(storage);
            faces = cvHaarDetectObjects(grayImage, classifier, storage, 1.1, 3,
                    CV_HAAR_FIND_BIGGEST_OBJECT | CV_HAAR_DO_ROUGH_SEARCH);

            postInvalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            Paint paint = new Paint();
            paint.setColor(Color.RED);
            paint.setTextSize(20);

            String s = "FacePreview - This side up.";
            float textWidth = paint.measureText(s);
            canvas.drawText(s, (getWidth() - textWidth) / 2, 20, paint);

            if (faces != null) {
                paint.setStrokeWidth(2);
                paint.setStyle(Paint.Style.STROKE);
                float scaleX = (float) getWidth() / grayImage.width();
                float scaleY = (float) getHeight() / grayImage.height();
                int total = faces.total();
                for (int i = 0; i < total; i++) {
                    opencv_core.CvRect r = new opencv_core.CvRect(cvGetSeqElem(faces, i));
                    int x = r.x(), y = r.y(), w = r.width(), h = r.height();
                    canvas.drawRect(x * scaleX, y * scaleY, (x + w) * scaleX, (y + h) * scaleY, paint);
                }
            }
        }
    }

    class Preview extends SurfaceView implements SurfaceHolder.Callback {
        SurfaceHolder mHolder;
        Camera mCamera;
        Camera.PreviewCallback previewCallback;

        Preview(Context context, Camera.PreviewCallback previewCallback) {
            super(context);
            this.previewCallback = previewCallback;

            // Install a SurfaceHolder.Callback so we get notified when the
            // underlying surface is created and destroyed.
            mHolder = getHolder();
            mHolder.addCallback(this);
            mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }

        public void surfaceCreated(SurfaceHolder holder) {
            // The Surface has been created, acquire the camera and tell it where
            // to draw.
            mCamera = Camera.open();
            try {
                mCamera.setPreviewDisplay(holder);
            } catch (IOException exception) {
                mCamera.release();
                mCamera = null;
                // TODO: add more exception handling logic here
            }
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
            // Surface will be destroyed when we return, so stop the preview.
            // Because the CameraDevice object is not a shared resource, it's very
            // important to release it when the activity is paused.
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }


        private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
            final double ASPECT_TOLERANCE = 0.05;
            double targetRatio = (double) w / h;
            if (sizes == null) return null;

            Camera.Size optimalSize = null;
            double minDiff = Double.MAX_VALUE;

            int targetHeight = h;

            // Try to find an size match aspect ratio and size
            for (Camera.Size size : sizes) {
                double ratio = (double) size.width / size.height;
                if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }

            // Cannot find the one match the aspect ratio, ignore the requirement
            if (optimalSize == null) {
                minDiff = Double.MAX_VALUE;
                for (Camera.Size size : sizes) {
                    if (Math.abs(size.height - targetHeight) < minDiff) {
                        optimalSize = size;
                        minDiff = Math.abs(size.height - targetHeight);
                    }
                }
            }
            return optimalSize;
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            // Now that the size is known, set up the camera parameters and begin
            // the preview.
            Camera.Parameters parameters = mCamera.getParameters();

            List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
            Camera.Size optimalSize = getOptimalPreviewSize(sizes, w, h);
            parameters.setPreviewSize(optimalSize.width, optimalSize.height);

            mCamera.setParameters(parameters);
            if (previewCallback != null) {
                mCamera.setPreviewCallbackWithBuffer(previewCallback);
                Camera.Size size = parameters.getPreviewSize();
                byte[] data = new byte[size.width * size.height *
                        ImageFormat.getBitsPerPixel(parameters.getPreviewFormat()) / 8];
                mCamera.addCallbackBuffer(data);
            }
            mCamera.startPreview();
        }
    }
}
