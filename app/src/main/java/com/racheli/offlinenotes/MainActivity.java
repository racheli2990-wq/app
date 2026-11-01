package com.racheli.offlinenotes;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int PICK_IMAGE=101, PERMISSION_RECORD=102, PERMISSION_SPEECH=103;
    private static final int BG=Color.rgb(246,247,251), INK=Color.rgb(35,39,58);
    private static final int MUTED=Color.rgb(119,124,145), PURPLE=Color.rgb(101,88,211);
    private static final int LINE=Color.rgb(231,233,241);

    private NotesDb db;
    private LinearLayout root, list;
    private EditText search,titleField,bodyField;
    private ImageView imagePreview;
    private TextView imageInfo,audioInfo,speechInfo,recordButton,playButton;
    private Note current;
    private MediaRecorder recorder;
    private MediaPlayer player;
    private SpeechRecognizer speech;
    private boolean recording=false;
    private String recordingPath;
    private String permissionAction="";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        db=new NotesDb(this);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setContentView(root);
        showList();
    }

    private void showList() {
        stopPlayer();
        current=null; titleField=null; bodyField=null;
        root.removeAllViews(); root.setPadding(dp(20),dp(18),dp(20),dp(12));
        LinearLayout top=row();
        TextView logo=label("✦",23,Color.WHITE,true);
        logo.setGravity(Gravity.CENTER); logo.setBackground(round(PURPLE,16));
        top.addView(logo,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout branding=column(); branding.setPadding(dp(12),0,0,0);
        branding.addView(label("פתקים מקומיים",19,INK,true));
        branding.addView(label("המילים שלך. רק אצלך.",12,MUTED,false));
        top.addView(branding,new LinearLayout.LayoutParams(0,-2,1));
        TextView add=button("＋ חדש",PURPLE,Color.WHITE);
        add.setOnClickListener(v->openNote(0)); top.addView(add);
        root.addView(top,new LinearLayout.LayoutParams(-1,dp(54)));
        gap(root,23);
        root.addView(label("כל הרעיונות שלך",25,INK,true));
        root.addView(label("פתקים, תמונות והקלטות — גם בלי רשת",13,MUTED,false));
        gap(root,17);
        search=new EditText(this);
        search.setSingleLine(true); search.setTextSize(14); search.setTextColor(INK); search.setHintTextColor(MUTED);
        search.setHint("⌕   חיפוש בפתקים..."); search.setPadding(dp(15),0,dp(15),0);
        search.setBackground(round(Color.WHITE,14,LINE,1)); search.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.addView(search,new LinearLayout.LayoutParams(-1,dp(52)));
        gap(root,13);
        ScrollView sc=new ScrollView(this); sc.setFillViewport(true);
        list=column(); sc.addView(list,new ScrollView.LayoutParams(-1,-2));
        root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        TextView footer=label("◉  נשמר במכשיר בלבד · ללא ענן",11,MUTED,false);
        footer.setGravity(Gravity.CENTER); footer.setPadding(0,dp(12),0,dp(4)); root.addView(footer);
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int count,int after){}
            public void onTextChanged(CharSequence s,int st,int before,int count){refreshList(s.toString());}
            public void afterTextChanged(Editable e){}
        });
        refreshList("");
    }

    private void refreshList(String query) {
        if(list==null)return;
        list.removeAllViews();
        String q=query==null?"":query.trim().toLowerCase(Locale.ROOT);
        int shown=0;
        for(Note n:db.all()){
            if(!q.isEmpty()&&!(n.title+" "+n.body).toLowerCase(Locale.ROOT).contains(q))continue;
            list.addView(noteCard(n),cardParams()); shown++;
        }
        if(shown==0){
            LinearLayout empty=column(); empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20),dp(42),dp(20),dp(42)); empty.setBackground(round(Color.WHITE,18,LINE,1));
            TextView icon=label(q.isEmpty()?"✎":"⌕",35,PURPLE,true); icon.setGravity(Gravity.CENTER); empty.addView(icon);
            TextView heading=label(q.isEmpty()?"הפתק הראשון מתחיל כאן":"לא נמצאו פתקים",17,INK,true);
            heading.setGravity(Gravity.CENTER); empty.addView(heading);
            TextView sub=label(q.isEmpty()?"כתבי, צרפי תמונה או הקליטי מחשבה.":"נסי לחפש במילים אחרות.",13,MUTED,false);
            sub.setGravity(Gravity.CENTER); empty.addView(sub);
            if(q.isEmpty()){TextView c=button("＋ יצירת פתק",PURPLE,Color.WHITE); c.setOnClickListener(v->openNote(0));
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(44)); p.topMargin=dp(18); empty.addView(c,p);}
            list.addView(empty,new LinearLayout.LayoutParams(-1,-2));
        }
    }

    private View noteCard(Note n) {
        LinearLayout card=column(); card.setPadding(dp(16),dp(14),dp(16),dp(14));
        card.setBackground(round(Color.WHITE,17,LINE,1)); card.setClickable(true); card.setFocusable(true);
        card.setOnClickListener(v->openNote(n.id));
        LinearLayout head=row();
        TextView name=label(n.title.trim().isEmpty()?"פתק ללא כותרת":n.title,16,INK,true);
        name.setMaxLines(1); name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        head.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        head.addView(label(date(n.updatedAt),10,MUTED,false)); card.addView(head);
        if(!n.body.trim().isEmpty()){TextView b=label(n.body,13,MUTED,false); b.setMaxLines(2); b.setEllipsize(android.text.TextUtils.TruncateAt.END);
            b.setPadding(0,dp(8),0,dp(2)); card.addView(b);}
        LinearLayout tags=row();
        if(exists(n.imagePath))tags.addView(chip("▧  תמונה"));
        if(exists(n.audioPath))tags.addView(chip("♫  הקלטה"));
        if(tags.getChildCount()>0){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(7); card.addView(tags,p);}
        return card;
    }

    private void openNote(long id) {
        stopPlayer();
        current=id>0?db.get(id):new Note(0,"","",null,null,System.currentTimeMillis());
        if(current==null)current=new Note(0,"","",null,null,System.currentTimeMillis());
        showEditor();
    }

    private void showEditor() {
        root.removeAllViews(); root.setPadding(dp(18),dp(10),dp(18),dp(10));
        LinearLayout bar=row();
        TextView back=button("‹",Color.WHITE,INK); back.setTextSize(24); back.setOnClickListener(v->saveAndClose());
        bar.addView(back,new LinearLayout.LayoutParams(dp(46),dp(44)));
        TextView screen=label(current.id>0?"עריכת פתק":"פתק חדש",16,INK,true); screen.setGravity(Gravity.CENTER);
        bar.addView(screen,new LinearLayout.LayoutParams(0,-2,1));
        TextView save=button("שמירה",PURPLE,Color.WHITE); save.setOnClickListener(v->saveAndClose()); bar.addView(save);
        root.addView(bar,new LinearLayout.LayoutParams(-1,dp(48)));
        ScrollView sc=new ScrollView(this);
        LinearLayout form=column(); form.setPadding(0,dp(17),0,dp(22)); sc.addView(form);
        root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        form.addView(label("כותרת",12,MUTED,true));
        titleField=new EditText(this); titleField.setSingleLine(true); titleField.setTextSize(20);
        titleField.setTypeface(Typeface.DEFAULT,Typeface.BOLD); titleField.setTextColor(INK);
        titleField.setHintTextColor(Color.rgb(177,180,194)); titleField.setHint("על מה נכתוב היום?");
        titleField.setText(current.title); titleField.setPadding(dp(14),dp(7),dp(14),dp(7));
        titleField.setBackground(round(Color.WHITE,13,LINE,1));
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,dp(56)); tp.topMargin=dp(7); form.addView(titleField,tp);
        LinearLayout.LayoutParams bl=new LinearLayout.LayoutParams(-1,-2); bl.topMargin=dp(19);
        form.addView(label("התוכן שלך",12,MUTED,true),bl);
        bodyField=new EditText(this); bodyField.setTextSize(16); bodyField.setTextColor(INK);
        bodyField.setHintTextColor(Color.rgb(177,180,194)); bodyField.setHint("כתבי כאן בחופשיות...\n\nכל מה שחשוב לך נשאר במכשיר.");
        bodyField.setGravity(Gravity.TOP|Gravity.RIGHT); bodyField.setMinLines(7);
        bodyField.setText(current.body); bodyField.setPadding(dp(14),dp(12),dp(14),dp(12));
        bodyField.setBackground(round(Color.WHITE,14,LINE,1));
        LinearLayout.LayoutParams bodyP=new LinearLayout.LayoutParams(-1,dp(205)); bodyP.topMargin=dp(7); form.addView(bodyField,bodyP);
        LinearLayout.LayoutParams sectionLabel=new LinearLayout.LayoutParams(-1,-2); sectionLabel.topMargin=dp(22);
        form.addView(label("קבצים מצורפים",12,MUTED,true),sectionLabel);

        LinearLayout photo=sectionCard(); LinearLayout photoTop=row();
        photoTop.addView(label("▧",24,PURPLE,true),new LinearLayout.LayoutParams(dp(39),dp(42)));
        LinearLayout desc=column(); desc.addView(label("תמונה",14,INK,true)); desc.addView(label("צילום או תמונה מהגלריה",11,MUTED,false));
        photoTop.addView(desc,new LinearLayout.LayoutParams(0,-2,1));
        TextView choose=button("בחירה",PURPLE,Color.WHITE); choose.setOnClickListener(v->chooseImage()); photoTop.addView(choose);
        photo.addView(photoTop);
        if(exists(current.imagePath)){
            imagePreview=new ImageView(this); imagePreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imagePreview.setBackground(round(BG,12)); imagePreview.setClipToOutline(true);
            imagePreview.setImageBitmap(decode(current.imagePath));
            LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,dp(165)); ip.topMargin=dp(10); photo.addView(imagePreview,ip);
            TextView remove=button("הסרת התמונה",Color.rgb(255,239,240),Color.rgb(179,55,68));
            remove.setOnClickListener(v->{removePrivateFile(current.imagePath);current.imagePath=null;save(false);showEditor();});
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-2,dp(38)); rp.topMargin=dp(7); photo.addView(remove,rp);
        }
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,-2); pp.topMargin=dp(9); form.addView(photo,pp);

        LinearLayout audio=sectionCard(); LinearLayout audioTop=row();
        audioTop.addView(label("♫",25,PURPLE,true),new LinearLayout.LayoutParams(dp(39),dp(42)));
        LinearLayout audioDesc=column(); audioDesc.addView(label("הקלטת קול",14,INK,true));
        audioInfo=label(audioDescription(),11,MUTED,false); audioDesc.addView(audioInfo);
        audioTop.addView(audioDesc,new LinearLayout.LayoutParams(0,-2,1));
        recordButton=button(recording?"■ עצירה":"● הקלטה",recording?Color.rgb(193,65,75):PURPLE,Color.WHITE);
        recordButton.setOnClickListener(v->toggleRecording()); audioTop.addView(recordButton); audio.addView(audioTop);
        playButton=null;
        if(exists(current.audioPath)){playButton=button("▶ השמעה",Color.rgb(240,239,255),PURPLE);
            playButton.setOnClickListener(v->playAudio()); LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-2,dp(38)); ap.topMargin=dp(8); audio.addView(playButton,ap);}
        LinearLayout.LayoutParams aup=new LinearLayout.LayoutParams(-1,-2); aup.topMargin=dp(10); form.addView(audio,aup);

        LinearLayout transcribe=sectionCard(); LinearLayout tr=row();
        tr.addView(label("✦",22,PURPLE,true),new LinearLayout.LayoutParams(dp(39),dp(42)));
        LinearLayout speechDesc=column(); speechDesc.addView(label("תמלול מקומי",14,INK,true));
        speechInfo=label("זיהוי דיבור במכשיר, ללא שליחת קול לשרת",11,MUTED,false); speechDesc.addView(speechInfo);
        tr.addView(speechDesc,new LinearLayout.LayoutParams(0,-2,1));
        TextView dictate=button("הכתבה",Color.rgb(240,239,255),PURPLE); dictate.setOnClickListener(v->startDictation()); tr.addView(dictate);
        transcribe.addView(tr); LinearLayout.LayoutParams trp=new LinearLayout.LayoutParams(-1,-2); trp.topMargin=dp(10); form.addView(transcribe,trp);

        TextView privacy=label("🔒  לא נדרש חשבון. האפליקציה אינה משתמשת ברשת.",11,MUTED,false);
        privacy.setGravity(Gravity.CENTER); LinearLayout.LayoutParams prv=new LinearLayout.LayoutParams(-1,-2); prv.topMargin=dp(16); form.addView(privacy,prv);
        if(current.id>0){TextView del=button("מחיקת הפתק",Color.rgb(255,239,240),Color.rgb(179,55,68));
            del.setOnClickListener(v->confirmDelete()); LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(-1,dp(43)); dp.topMargin=dp(20); form.addView(del,dp);}
    }

    private void chooseImage() {
        save(false);
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("image/*");
        startActivityForResult(i,PICK_IMAGE);
    }

    private void toggleRecording() {
        if(recording){stopRecording();return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            permissionAction="record"; requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},PERMISSION_RECORD); return;
        }
        startRecording();
    }

    private void startRecording() {
        try{
            save(false);
            File dir=new File(getFilesDir(),"audio"); if(!dir.exists()&&!dir.mkdirs())throw new Exception("לא ניתן ליצור תיקיית הקלטות");
            File f=new File(dir,"voice_"+System.currentTimeMillis()+".m4a");
            recorder=Build.VERSION.SDK_INT>=31?new MediaRecorder(this):new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(96000); recorder.setAudioSamplingRate(44100);
            recorder.setOutputFile(f.getAbsolutePath()); recorder.prepare(); recorder.start();
            recordingPath=f.getAbsolutePath(); current.audioPath=recordingPath; recording=true;
            recordButton.setText("■ עצירה"); recordButton.setBackground(round(Color.rgb(193,65,75),11));
            audioInfo.setText("מקליטה עכשיו... לחצי לעצירה");
        }catch(Exception e){releaseRecorder();recording=false;toast("לא ניתן להקליט: "+message(e));}
    }

    private void stopRecording() {
        try{if(recorder!=null)recorder.stop();}
        catch(Exception e){removePrivateFile(recordingPath);current.audioPath=null;toast("ההקלטה לא נשמרה. נסי שוב.");}
        finally{
            releaseRecorder(); recording=false; save(false); showEditor();
            toast(current.audioPath==null?"ההקלטה נכשלה":"ההקלטה נשמרה בפתק");
        }
    }

    private void releaseRecorder() {
        if(recorder!=null){try{recorder.reset();}catch(Exception ignored){}try{recorder.release();}catch(Exception ignored){}recorder=null;}
    }

    private void playAudio() {
        if(!exists(current.audioPath)){toast("לא נמצאה הקלטה");return;}
        stopPlayer();
        try{
            player=new MediaPlayer(); player.setDataSource(current.audioPath);
            player.setOnCompletionListener(mp->stopPlayer());
            player.setOnErrorListener((mp,w,e)->{stopPlayer();toast("לא ניתן להשמיע את ההקלטה");return true;});
            player.prepare();player.start();toast("מנגנת הקלטה");
        }catch(Exception e){stopPlayer();toast("לא ניתן להשמיע: "+message(e));}
    }

    private void stopPlayer() {
        if(player!=null){try{if(player.isPlaying())player.stop();}catch(Exception ignored){}try{player.release();}catch(Exception ignored){}player=null;}
    }

    private void startDictation() {
        if(recording){toast("עצרי את ההקלטה לפני תמלול");return;}
        if(Build.VERSION.SDK_INT<31){speechInfo.setText("תמלול מקומי דורש Android 12 ומעלה");return;}
        if(!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)){
            speechInfo.setText("אין במכשיר מנוע זיהוי דיבור לא-מקוון");
            new AlertDialog.Builder(this).setTitle("תמלול מקומי לא זמין")
                .setMessage("כדי לתמלל ללא אינטרנט, המכשיר צריך לתמוך בזיהוי דיבור On-device ולהכיל מודל עברית לא-מקוון. בדקי בהגדרות השפה וההקלדה אם קיימת הורדת שפה.")
                .setPositiveButton("הבנתי",null).show(); return;
        }
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            permissionAction="speech";requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},PERMISSION_SPEECH);return;
        }
        beginDictation();
    }

    private void beginDictation() {
        try{
            save(false); stopSpeech();
            speech=SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
            speech.setRecognitionListener(new RecognitionListener(){
                public void onReadyForSpeech(Bundle b){speechInfo.setText("מקשיבה... דברי בעברית");}
                public void onBeginningOfSpeech(){speechInfo.setText("הדיבור נקלט");}
                public void onRmsChanged(float v){}
                public void onBufferReceived(byte[] b){}
                public void onEndOfSpeech(){speechInfo.setText("מעבדת תמלול במכשיר...");}
                public void onError(int error){
                    speechInfo.setText("התמלול לא הצליח. בדקי את המיקרופון ומודל השפה.");
                    toast(error==SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED||error==SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
                        ?"מודל עברית לא-מקוון אינו מותקן במכשיר.":"שגיאה בתמלול ("+error+"). נסי שוב במקום שקט.");
                    stopSpeech();
                }
                public void onResults(Bundle b){
                    ArrayList<String> matches=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if(matches!=null&&!matches.isEmpty()){
                        String old=bodyField.getText().toString().trim();
                        bodyField.setText(old.isEmpty()?matches.get(0):old+"\n\n"+matches.get(0));
                        bodyField.setSelection(bodyField.length()); save(false);
                        speechInfo.setText("התמלול נוסף לפתק ונשמר במכשיר");
                    }else speechInfo.setText("לא זוהה דיבור. אפשר לנסות שוב.");
                    stopSpeech();
                }
                public void onPartialResults(Bundle b){}
                public void onEvent(int t,Bundle b){}
            });
            Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"he-IL");
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,"he-IL");
            i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false);
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
            speech.startListening(i); speechInfo.setText("מפעילה תמלול במכשיר...");
        }catch(Exception e){speechInfo.setText("לא ניתן להפעיל תמלול");toast(message(e));stopSpeech();}
    }

    private void stopSpeech() {
        if(speech!=null){try{speech.stopListening();}catch(Exception ignored){}try{speech.destroy();}catch(Exception ignored){}speech=null;}
    }

    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(code,permissions,grants);
        if((code==PERMISSION_RECORD||code==PERMISSION_SPEECH)&&grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED){
            String action=permissionAction;permissionAction="";
            if("record".equals(action))startRecording();else if("speech".equals(action))beginDictation();
        }else if(code==PERMISSION_RECORD||code==PERMISSION_SPEECH){permissionAction="";toast("נדרשת הרשאת מיקרופון");}
    }

    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==PICK_IMAGE&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            try{
                File dir=new File(getFilesDir(),"images");if(!dir.exists()&&!dir.mkdirs())throw new Exception("לא ניתן ליצור תיקייה");
                File file=new File(dir,"image_"+System.currentTimeMillis()+".img");
                try(InputStream in=getContentResolver().openInputStream(data.getData());FileOutputStream out=new FileOutputStream(file)){
                    if(in==null)throw new Exception("לא ניתן לפתוח את התמונה");
                    byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
                }
                removePrivateFile(current.imagePath);current.imagePath=file.getAbsolutePath();save(false);showEditor();toast("התמונה נוספה לפתק");
            }catch(Exception e){toast("לא ניתן לצרף תמונה: "+message(e));}
        }
    }

    private void save(boolean back) {
        if(current==null)return;
        if(titleField!=null)current.title=titleField.getText().toString().trim();
        if(bodyField!=null)current.body=bodyField.getText().toString();
        if(current.title.isEmpty()&&current.body.trim().isEmpty()&&current.imagePath==null&&current.audioPath==null){if(back)showList();return;}
        current.updatedAt=System.currentTimeMillis();current.id=db.save(current);
        if(back)showList();
    }
    private void saveAndClose(){hideKeyboard();save(true);}
    @Override public void onBackPressed(){if(current!=null&&titleField!=null)saveAndClose();else super.onBackPressed();}

    private void confirmDelete() {
        new AlertDialog.Builder(this).setTitle("למחוק את הפתק?")
            .setMessage("הפתק והקבצים המצורפים יימחקו מהמכשיר.")
            .setNegativeButton("ביטול",null).setPositiveButton("מחיקה",(d,w)->{
                db.delete(current.id);removePrivateFile(current.imagePath);removePrivateFile(current.audioPath);current=null;showList();
            }).show();
    }

    private void hideKeyboard(){
        try{InputMethodManager im=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if(im!=null&&getCurrentFocus()!=null)im.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),0);}catch(Exception ignored){}
    }
    @Override protected void onDestroy(){
        if(recording){try{if(recorder!=null)recorder.stop();}catch(Exception ignored){}releaseRecorder();}
        stopPlayer();stopSpeech();if(db!=null)db.close();super.onDestroy();
    }

    private String audioDescription(){return recording?"מקליטה עכשיו...":exists(current.audioPath)?"הקלטה שמורה בפתק":"הקלטה פרטית במכשיר";}
    private BitmapFactory.Options imageOptions(){BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=2;return o;}
    private android.graphics.Bitmap decode(String path){try{return BitmapFactory.decodeFile(path,imageOptions());}catch(Exception e){return null;}}
    private boolean exists(String p){return p!=null&&new File(p).exists();}
    private void removePrivateFile(String p){if(p!=null)try{new File(p).delete();}catch(Exception ignored){}}
    private String date(long t){return DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT,new Locale("he","IL")).format(new Date(t));}
    private String message(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout sectionCard(){LinearLayout l=column();l.setPadding(dp(13),dp(10),dp(13),dp(11));l.setBackground(round(Color.WHITE,15,LINE,1));return l;}
    private LinearLayout.LayoutParams cardParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);return p;}
    private void gap(LinearLayout parent,int h){parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
    private TextView label(String text,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);return t;
    }
    private TextView button(String text,int bg,int fg){
        TextView t=label(text,13,fg,true);t.setGravity(Gravity.CENTER);t.setPadding(dp(13),dp(7),dp(13),dp(7));
        t.setBackground(round(bg,11));t.setClickable(true);t.setFocusable(true);return t;
    }
    private TextView chip(String text){TextView t=label(text,10,PURPLE,true);t.setPadding(dp(8),dp(4),dp(8),dp(4));t.setBackground(round(Color.rgb(242,240,255),20));return t;}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private GradientDrawable round(int color,int radius,int stroke,int width){GradientDrawable d=round(color,radius);d.setStroke(dp(width),stroke);return d;}
}
