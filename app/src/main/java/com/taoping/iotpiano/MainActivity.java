package com.taoping.iotpiano;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.annotation.SuppressLint;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewAnimator;

import com.taoping.notes.Note;
import com.taoping.notes.NoteQueue;

import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private PianoKeyboardView keyboard;
    private TextView noteText;
    private TextView ipText;
    private Button searchIPBtn;
    private Button sendNoteBtn;
    private TextView noteCoverText;
    private boolean muteSound = false; //手机是否播放音，默认播放
    private AssetManager assetManager; //在MainActivity中初始化
    private long keyPreviousInterval; //上一个键被按下的时间，单位毫秒
    private int keyPreviousPressed = -1;

    @SuppressLint({"ClickableViewAccessibility", "SetTextI18n"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Objects.requireNonNull(getSupportActionBar()).hide();
        @SuppressLint("UseSwitchCompatOrMaterialCode") final Switch transmissionSwitch = findViewById(R.id.switchWifiIR);
        @SuppressLint("UseSwitchCompatOrMaterialCode") final Switch muteSwitch = findViewById(R.id.muteSwitch);
        searchIPBtn = (Button) findViewById(R.id.searchReceiverBtn);
        sendNoteBtn = (Button) findViewById(R.id.sendNoteBtn);
        keyboard = (PianoKeyboardView) findViewById(R.id.piano_keyboard_view);
        assetManager = getAssets();
        noteText = (TextView) findViewById(R.id.noteText);
        ipText = (TextView) findViewById(R.id.ipText);
        noteCoverText = (TextView) findViewById(R.id.coverTextView);
        RadioGroup toneCoverageRadio = (RadioGroup) findViewById(R.id.toneRadioGroup);
        transmissionSwitch.setOnClickListener(view -> {
            if(transmissionSwitch.isChecked()){
                transmissionSwitch.setText("   IR");
                NoteQueue.sendingChannel = "IR";
                ipText.setText("IR Sensor");
                searchIPBtn.setVisibility(View.INVISIBLE);
                showMessage("send notes via IR");
            }else{
                transmissionSwitch.setText("Wifi");
                NoteQueue.sendingChannel = "WIFI";
                ipText.setText(ReceiverSearcher.receiverIP + ":8888");
                searchIPBtn.setVisibility(View.VISIBLE);
                showMessage("send notes via WIFI");
            }
        });
        muteSwitch.setOnClickListener(view -> {
            if(muteSwitch.isChecked()){
                muteSwitch.setText("Unmuted");
                muteSound = false;
                showMessage("Unmuted.");
            }else{
                muteSwitch.setText("Muted");
                muteSound = true;
                showMessage("Muted.");
            }
        });
        searchIPBtn.setOnClickListener(v -> {
            searchIPBtn.setEnabled(false);
            searchReceiverIP();
            // Perform action on click
            showMessage("Searching for IP receiver...");
        });
        sendNoteBtn.setOnClickListener(v -> {
            //把最后一个note加进去
            if(keyPreviousPressed != -1){
                NoteQueue.addNote(new Note(keyPreviousPressed, (int)(System.currentTimeMillis() - keyPreviousInterval)));
            }
            //发送完成之后，要清空前面的键
            keyPreviousPressed = -1;
//            sendNoteBtn.setEnabled(false);
            NoteQueue.sendNotes();
            // Perform action on click
            showMessage("Sending notes...");
        });

        //点击后返回true，避免触发下面的琴键的onTouch事件
        noteCoverText.setOnTouchListener((view, motionEvent) -> {
            return true;
        });
        keyboard.setOnTouchListener((view, motionEvent) -> {
            keyboard.pianoOnTouch(motionEvent);
            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN && keyboard.pressedKey != -1)
                notePressDown();
            else if (motionEvent.getAction() == MotionEvent.ACTION_UP)
                notePressUp();
            return true;
        });

        toneCoverageRadio.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                 // checkedId is the RadioButton selected
                if(checkedId == R.id.lowToneRadioButton){
                    NoteQueue.keyboardToneLevel = "LOW";
                    coverUnreachableKeys();
                    showMessage("low tone keyboard.");
//                    Log.d("radiobutton", "LOW");
                }else if(checkedId == R.id.midToneRadioButton){
                    NoteQueue.keyboardToneLevel = "MID";
                    coverUnreachableKeys();
                    showMessage("middle tone keyboard.");
//                    Log.d("radiobutton", "MID");
                }else{
                    NoteQueue.keyboardToneLevel = "HIG";
                    coverUnreachableKeys();
                    showMessage("high tone keyboard.");
//                    Log.d("radiobutton", "HIG");
                }
            }});
    }

    //局域网内广播消息，查找服务器
    private void searchReceiverIP() {
        try {
            ReceiverSearcher.searchReceiver(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setIpText(String ip){
        Looper.prepare();
//        searchIPBtn.setEnabled(true);
//        Looper.prepare();
        this.ipText.setText(ip);
        if(ip.startsWith("No"))
            showMessage("No IP receiver found.");
        else
            showMessage("Found a IP receiver " + ip);
        Looper.loop();
    }

    private void notePressDown(){
        long currentInterval = System.currentTimeMillis();
        //不是第一次按，就把前一个按键加进去
        if(keyPreviousPressed != -1){
            NoteQueue.addNote(new Note(keyPreviousPressed, (int)(currentInterval - keyPreviousInterval)));
        }
        keyPreviousInterval = currentInterval;
        keyPreviousPressed = keyboard.pressedKey;
        playNoteSound();
        ViewGroup.LayoutParams noteTextParam = noteText.getLayoutParams();
        if(keyboard.isWhiteKey(keyboard.pressedKey))
            noteTextParam.width = PianoKeyboardView.WHITE_KEY_WIDTH;
        else
            noteTextParam.width = PianoKeyboardView.BLACK_KEY_WIDTH;
        noteText.setLayoutParams(noteTextParam);
        ViewAnimator animator = findViewById(R.id.animator);
        animator.clearAnimation();
        noteText.setText(PianoKeyboardView.keyNoteName.get(keyboard.pressedKey % 12));
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) noteText.getLayoutParams();
        params.leftMargin = keyboard.getXByKeyIndex();
        noteText.setLayoutParams(params);
        //是不是用线程来做，否则主UI卡住
//        IRSender.sendIRNote(this, keyboard.pressedKey);
    }

    private void notePressUp(){
//        Log.d(TAG, "notePressDown: " + System.currentTimeMillis() + ":  " + keyboard.pressedKey);
        ViewAnimator animator = findViewById(R.id.animator);
        AlphaAnimation animation = new AlphaAnimation(1, 0);
        animation.setDuration(1000);
        animation.setFillAfter(true);
        animator.startAnimation(animation);
        animator.showNext();
    }

    private void showMessage(String msg){
        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
    }

    //选择低音区或者高音区的时候，将不可用的键隐藏起来
    private void coverUnreachableKeys(){
        ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(
                (keyboard.WHITE_KEY_WIDTH + 10) * 5,
                keyboard.getHeight());
        params.leftToLeft = ConstraintLayout.LayoutParams.UNSET;
        params.rightToRight = ConstraintLayout.LayoutParams.UNSET;
        if(NoteQueue.keyboardToneLevel.equals("LOW")){
            noteCoverText.setVisibility(View.VISIBLE);
            params.leftToLeft = R.id.piano_keyboard_view;
            noteCoverText.setLayoutParams(params);
        }else if(NoteQueue.keyboardToneLevel.equals("HIG")){
            noteCoverText.setVisibility(View.VISIBLE);
            params.width = (keyboard.WHITE_KEY_WIDTH + 10) * 6 + 10;
            params.rightToRight = R.id.piano_keyboard_view;
            noteCoverText.setLayoutParams(params);
        }else{
            noteCoverText.setVisibility(View.INVISIBLE);
        }

    }

    //播放对应的音
    private void playNoteSound() {
//        Log.d(TAG, "playNoteSound: " + NoteQueue.keyboardToneLevel + ", " + keyboard.pressedKey + "; " + keyboard.note2Mp3File.get(NoteQueue.keyboardToneLevel+keyboard.pressedKey));
        if(!muteSound){
            try {
                AssetFileDescriptor afd = assetManager.openFd(keyboard.note2Mp3File.get(NoteQueue.keyboardToneLevel+keyboard.pressedKey));
//                Log.d(TAG, "playNoteSound: " + keyboard.note2Mp3File.get(NoteQueue.keyboardToneLevel+keyboard.pressedKey));
                MediaPlayer mediaPlayer = new MediaPlayer();
                mediaPlayer.reset();
                mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                mediaPlayer.prepare();
                mediaPlayer.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}