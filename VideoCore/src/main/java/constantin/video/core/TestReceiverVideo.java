package constantin.video.core;

import android.app.Activity;
import android.graphics.Color;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.OnLifecycleEvent;

import constantin.video.core.player.VideoPlayer;

//creates a new thread that -in between onResume() / onPause()
//constantly reads from videoPlayer and updates the appropriate ui elements
//if they are not null

public class TestReceiverVideo implements Runnable, LifecycleObserver {

    private final AppCompatActivity activity;
    private final VideoPlayer videoPlayer;
    private Thread mThread;

    private TextView receivedVideoDataTV;
    private Button button;

    public TestReceiverVideo(final AppCompatActivity parent){
        this.activity=parent;
        videoPlayer=new VideoPlayer(activity);
        parent.getLifecycle().addObserver(this);
    }

    public void setViews(TextView receivedVideoDataTV, Button connectB){
        this.receivedVideoDataTV = receivedVideoDataTV;
        this.button=connectB;
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    private void startUiUpdates(){
        videoPlayer.addAndStartDecoderReceiver(null);
        mThread=new Thread(this);
        mThread.setName("V_TestR");
        mThread.start();
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    private void stopUiUpdates(){
        mThread.interrupt();
        try {mThread.join();} catch (InterruptedException e) {e.printStackTrace();}
        videoPlayer.stopAndRemoveReceiverDecoder();
    }

    //to have as less work on the UI thread and GPU, we check if the content of the string has changed
    //before updating the TV.
    private static void updateViewIfStringChanged(final Activity activity,final TextView tv, final String newContent,final boolean colorRed){
        final String prev=tv.getText().toString();
        if(!prev.contentEquals(newContent)){
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    tv.setText(newContent);
                    if(colorRed){
                        tv.setTextColor(Color.RED);
                    }else{
                        tv.setTextColor(Color.GREEN);
                    }
                }
            });
        }
    }

    @Override
    public void run(){
        long lastCheckMS = System.currentTimeMillis() - 2*1000;
        while (!Thread.currentThread().isInterrupted()){
            //if the receivedVideoDataTV is !=null, we should update its content with the
            //number of received bytes usw
            if(receivedVideoDataTV !=null){
                final String s= VideoPlayer.getVideoInfoString(videoPlayer.getNativeInstance());
                updateViewIfStringChanged(activity,receivedVideoDataTV,s,!VideoPlayer.anyVideoDataReceived(videoPlayer.getNativeInstance()));
            }
            if(button!=null){
                final boolean currentlyReceivingData=VideoPlayer.anyVideoBytesParsedSinceLastCall(videoPlayer.getNativeInstance());
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        button.setTextColor(currentlyReceivingData ? Color.GREEN : Color.RED);
                    }
                });
            }
            //Every N s we check if we are receiving video data, but cannot parse the data. Probably the user did mix up
            //rtp and raw. Make a warning toast
            if(System.currentTimeMillis()-lastCheckMS>=3000){
                final boolean errorVideo=VideoPlayer.receivingVideoButCannotParse(videoPlayer.getNativeInstance());
                lastCheckMS =System.currentTimeMillis();
                if(errorVideo){
                    makeToastOnUI("You are receiving video data, but FPV-VR cannot parse it. If you are using RAW, please disable" +
                            " rtp parsing via NETWORK-->UseRTP or change ez-wb config to rtp",Toast.LENGTH_LONG);
                }
            }
            //Refresh every 200ms
            try {Thread.sleep(200);} catch (InterruptedException e) {return;}
        }
    }
    private void makeToastOnUI(final String s,final int length){
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(activity,s,length).show();
            }
        });
    }

}
