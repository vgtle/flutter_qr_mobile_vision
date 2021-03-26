package com.github.rmtmckenzie.qrmobilevision;

import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.barcode.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;

import java.util.List;

/**
 * Allows QrCamera classes to send frames to a Detector
 */

class QrDetector implements OnSuccessListener<List<Barcode>>, OnFailureListener {
    private static final String TAG = "cgr.qrmv.QrDetector";
    private static final String RETURN_TYPE_DISPLAY = "ReturnFormats.DISPLAY";
    private final QrReaderCallbacks communicator;
    private final BarcodeScanner detector;
    private final String returnValueType;

    public interface Frame {
        InputImage toImage();

        void close();
    }

    @GuardedBy("this")
    private Frame latestFrame;

    @GuardedBy("this")
    private Frame processingFrame;

    QrDetector(QrReaderCallbacks communicator, BarcodeScannerOptions options, String returnValueType) {
        this.communicator = communicator;
        this.detector = BarcodeScanning.getClient(options);
        this.returnValueType = returnValueType;
    }

    void detect(Frame frame) {
        if (latestFrame != null) latestFrame.close();
        latestFrame = frame;

        if (processingFrame == null) {
            processLatest();
        }
    }

    private synchronized void processLatest() {
        if (processingFrame != null) processingFrame.close();
        processingFrame = latestFrame;
        latestFrame = null;
        if (processingFrame != null) {
            processFrame(processingFrame);
        }
    }

    private void processFrame(Frame frame) {
        InputImage image;
        try {
            image = frame.toImage();
        } catch (IllegalStateException ex) {
            // ignore state exception from making frame to image
            // as the image may be closed already.
            return;
        }

        detector.process(image)
            .addOnSuccessListener(this)
            .addOnFailureListener(this);
    }

    @Override
    public void onSuccess(List<Barcode> firebaseVisionBarcodes) {
        for (Barcode barcode : firebaseVisionBarcodes) {
            communicator.qrRead(RETURN_TYPE_DISPLAY.equalsIgnoreCase(returnValueType) ? barcode.getDisplayValue() : barcode.getRawValue());
        }
        processLatest();
    }

    @Override
    public void onFailure(@NonNull Exception e) {
        Log.w(TAG, "Barcode Reading Failure: ", e);
    }
}
