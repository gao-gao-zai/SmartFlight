package com.gaozay.smartflight.activityfixture;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.graphics.Color;
import android.widget.*;
public class FixtureActivity extends Activity {
 private TextView state;
 private Handler handler=new Handler();
 @Override public void onCreate(Bundle saved) {
  super.onCreate(saved);
  LinearLayout root=new LinearLayout(this); root.setOrientation(1); root.setPadding(22,20,22,16); root.setBackgroundColor(Color.rgb(248,246,240));
  boolean first=getClass().getSimpleName().equals("FirstActivity");
  TextView tag=new TextView(this); tag.setText("SMARTFLIGHT · 演示应用"); tag.setTextSize(12); tag.setTextColor(Color.rgb(120,130,140)); root.addView(tag);
  TextView title=new TextView(this); title.setText(first?"在线功能":"离线功能"); title.setTextSize(28); title.setTextColor(Color.rgb(35,40,48)); title.setPadding(0,12,0,10); root.addView(title);
  TextView desc=new TextView(this); desc.setText(first?"需要联网时，让规则帮你开启移动数据。":"同一个应用，也能为不同 Activity 配置不同规则。"); desc.setTextSize(16); root.addView(desc);
  state=new TextView(this); state.setTextSize(20); state.setPadding(0,20,0,14); root.addView(state);
  Button next=new Button(this); next.setText(first?"进入离线功能":"返回在线功能"); next.setOnClickListener(v->startActivity(new Intent(this,first?SecondActivity.class:FirstActivity.class))); root.addView(next);
  Button quick=new Button(this); quick.setText("快捷声明入口"); quick.setOnClickListener(v->startActivity(new Intent("com.gaozay.smartflight.action.QUICK_RULE").setPackage("com.gaozay.smartflight").putExtra("package_name",getPackageName()).putExtra("activity_name",getClass().getName()))); root.addView(quick);
  TextView component=new TextView(this); component.setText(getClass().getSimpleName()); component.setTextSize(12); component.setTextColor(Color.GRAY); root.addView(component);
  setContentView(root); handler.post(new Runnable(){public void run(){boolean on=Settings.Global.getInt(getContentResolver(),"mobile_data",0)==1; state.setText("移动数据："+(on?"已开启":"已关闭")); state.setTextColor(on?Color.rgb(25,123,177):Color.rgb(110,119,126));handler.postDelayed(this,300);}});
 }
}