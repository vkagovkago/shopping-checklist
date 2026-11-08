package com.example.shoppinglist;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.net.Uri;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Locale;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageView;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private final ExecutorService imageExecutor=Executors.newFixedThreadPool(3);
    private final Handler mainHandler=new Handler(Looper.getMainLooper());
    private final Map<String,Bitmap> imageCache=new HashMap<>();
    private final Set<String> imageRequests=new HashSet<>();
    private final Set<String> imageUnavailable=new HashSet<>();
    private final ArrayList<Item> items = new ArrayList<>();
    private LinearLayout list;
    private TextView summary, empty, dateLabel;
    private String date = today();
    private boolean dark;
    private int fontOffset;
    private JSONObject dates = new JSONObject();
    private EditText input;
    private static final int GREEN = Color.rgb(39, 111, 83);
    private static class Item {
        String name; boolean done;
        Item(String name, boolean done) { this.name = name; this.done = done; }
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        dark=getSharedPreferences("shopping",MODE_PRIVATE).getBoolean("dark",false);
        fontOffset=getSharedPreferences("shopping",MODE_PRIVATE).getInt("fontOffset",0);
        getWindow().setStatusBarColor(dark ? Color.rgb(26,32,29) : Color.rgb(245, 248, 244));
        getWindow().setNavigationBarColor(dark ? Color.rgb(26,32,29) : Color.rgb(245, 248, 244));
        getWindow().getDecorView().setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        loadAll();
        load();
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(16)); root.setBackgroundColor(dark ? Color.rgb(26,32,29) : Color.rgb(245,248,244));
        setContentView(root);
        TextView eyebrow = text("SHOPPING LIST", 12, GREEN); eyebrow.setTypeface(null, Typeface.BOLD); root.addView(eyebrow);
        TextView title = text("장보기 체크리스트", 27, Color.rgb(28,45,37)); title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1,-2); titleParams.setMargins(0,dp(6),0,dp(4)); root.addView(title,titleParams);
        summary = text("", 14, Color.rgb(103,120,109)); root.addView(summary);
        LinearLayout settings = new LinearLayout(this); settings.setGravity(Gravity.END); root.addView(settings);
        Button smaller=button("가 −",Color.TRANSPARENT,GREEN); settings.addView(smaller); smaller.setOnClickListener(v -> changeFont(-1));
        Button larger=button("가 +",Color.TRANSPARENT,GREEN); settings.addView(larger); larger.setOnClickListener(v -> changeFont(1));
        Button theme=button(dark ? "라이트 모드" : "다크 모드",Color.TRANSPARENT,GREEN); settings.addView(theme); theme.setOnClickListener(v -> { getSharedPreferences("shopping",MODE_PRIVATE).edit().putBoolean("dark",!dark).apply(); recreate(); });
        LinearLayout dateBar = new LinearLayout(this); dateBar.setGravity(Gravity.CENTER_VERTICAL); root.addView(dateBar);
        dateLabel = text("", 16, GREEN); dateLabel.setTypeface(null, Typeface.BOLD); dateBar.addView(dateLabel, new LinearLayout.LayoutParams(0,-2,1));
        Button chooseDate=button("날짜 선택",Color.TRANSPARENT,GREEN); dateBar.addView(chooseDate);
        chooseDate.setOnClickListener(v -> { String[] parts=date.split("-"); new DatePickerDialog(this,(view,y,m,d)-> { save(); date=String.format(Locale.US,"%04d-%02d-%02d",y,m+1,d); load(); render(); },Integer.parseInt(parts[0]),Integer.parseInt(parts[1])-1,Integer.parseInt(parts[2])).show(); });
        LinearLayout entry = new LinearLayout(this); entry.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams entryParams = new LinearLayout.LayoutParams(-1,dp(56)); entryParams.setMargins(0,dp(24),0,dp(18)); root.addView(entry,entryParams);
        input = new EditText(this); input.setSingleLine(true); input.setTextSize(16+fontOffset); input.setTextColor(dark ? Color.WHITE : Color.rgb(28,45,37)); input.setHintTextColor(dark ? Color.LTGRAY : Color.GRAY); input.setHint("무엇을 사야 하나요?"); input.setPadding(dp(14),0,dp(14),0); input.setBackground(bg(Color.WHITE,12)); input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        entry.addView(input,new LinearLayout.LayoutParams(0,-1,1));
        Button add = button("추가",GREEN,Color.WHITE); LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(dp(72),-1); addParams.setMargins(dp(8),0,0,0); entry.addView(add,addParams);
        add.setOnClickListener(v -> addItem());
        input.setOnEditorActionListener((v, action, event) -> { if (action == EditorInfo.IME_ACTION_DONE || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) { addItem(); return true; } return false; });
        LinearLayout actions = new LinearLayout(this); root.addView(actions);
        Button paste=button("목록 붙여넣기",Color.TRANSPARENT,GREEN); actions.addView(paste,new LinearLayout.LayoutParams(0,dp(48),1));
        paste.setOnClickListener(v -> pasteList());
        Button export=button("파일로 내보내기",Color.TRANSPARENT,GREEN); actions.addView(export,new LinearLayout.LayoutParams(0,dp(48),1));
        export.setOnClickListener(v -> { Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/json"); i.putExtra(Intent.EXTRA_TITLE,"shopping-list.json"); startActivityForResult(i,1); });
        Button restore=button("파일 불러오기",Color.TRANSPARENT,GREEN); actions.addView(restore,new LinearLayout.LayoutParams(0,dp(48),1));
        restore.setOnClickListener(v -> { Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*"); startActivityForResult(i,2); });
        LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL); root.addView(bar);
        TextView heading = text("내 장보기 목록", 17, Color.rgb(28,45,37)); heading.setTypeface(null,Typeface.BOLD); bar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        Button select = button("전체 선택",Color.TRANSPARENT,GREEN); select.setTextSize(13+fontOffset); bar.addView(select);
        select.setOnClickListener(v -> { if(items.isEmpty()) return; boolean all=true; for(Item i:items) if(!i.done) { all=false; break; } for(Item i:items) i.done=!all; save(); render(); });
        Button clear = button("완료 항목 정리",Color.TRANSPARENT,GREEN); clear.setTextSize(13+fontOffset); bar.addView(clear);
        clear.setOnClickListener(v -> { boolean any = false; for (Item i : items) if (i.done) { any = true; break; } if (!any) { Toast.makeText(this,"정리할 완료 항목이 없어요",Toast.LENGTH_SHORT).show(); return; } new AlertDialog.Builder(this).setMessage("구매 완료한 항목을 모두 삭제할까요?").setNegativeButton("취소",null).setPositiveButton("삭제",(d,w)-> { for (int i=items.size()-1;i>=0;i--) if(items.get(i).done) items.remove(i); save(); render(); }).show(); });
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list);
        empty = text("아직 추가한 항목이 없어요.\n필요한 물건을 위에 입력해 보세요!",15,Color.rgb(125,140,130)); empty.setGravity(Gravity.CENTER); render();
    }
    private void addItem() { String value=input.getText().toString().trim(); if(value.isEmpty()) { input.setError("항목을 입력해 주세요"); return; } int count=addNames(value); if(count==0) { input.setError("상품명을 입력해 주세요"); return; } input.setText(""); save(); render(); }
    private int addNames(String value) { int count=0; for(String line:value.split("[\\r\\n,/]+")) { String name=line.trim().replaceFirst("^(?:[-*•]\\s*|\\d+[.)]\\s*|\\[[ xX]\\]\\s*)+","").trim(); if(!name.isEmpty()) { items.add(new Item(name,false)); count++; } } return count; }
    private void editItem(Item item) { EditText edit=new EditText(this); edit.setSingleLine(true); edit.setText(item.name); edit.setSelection(edit.length()); AlertDialog dialog=new AlertDialog.Builder(this).setTitle("상품 수정").setView(edit).setNegativeButton("취소",null).setPositiveButton("저장",null).create(); dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w -> { String name=edit.getText().toString().trim(); if(name.isEmpty()) { edit.setError("상품명을 입력해 주세요"); return; } item.name=name; save(); render(); dialog.dismiss(); })); dialog.show(); }
    private void render() {
        dateLabel.setText(date + " 장보기");
        list.removeAllViews(); int done=0; for(Item i:items) if(i.done) done++;
        summary.setText("전체 " + items.size() + "개  ·  구매 완료 " + done + "개");
        if(items.isEmpty()) { if(empty.getParent()!=null) ((android.view.ViewGroup)empty.getParent()).removeView(empty); list.addView(empty,new LinearLayout.LayoutParams(-1,dp(180))); return; }
        ArrayList<Item> sorted=new ArrayList<>(items); java.util.Collections.sort(sorted, (a,b) -> Integer.compare(category(a.name),category(b.name)));
        int previous=-1;
        for(Item item:sorted) {
            int group=category(item.name); if(group!=previous) { TextView groupTitle=text(categoryName(group),15,GREEN); groupTitle.setTypeface(null,Typeface.BOLD); LinearLayout.LayoutParams groupParams=new LinearLayout.LayoutParams(-1,-2); groupParams.setMargins(dp(4),dp(18),0,dp(2)); list.addView(groupTitle,groupParams); previous=group; }
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(8),dp(3),dp(6),dp(3)); row.setBackground(bg(Color.WHITE,12));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,dp(8),0,0); list.addView(row,p);
            ImageView photo=new ImageView(this); photo.setContentDescription(item.name + " 이미지"); photo.setScaleType(ImageView.ScaleType.CENTER_CROP); photo.setBackground(bg(dark ? Color.rgb(65,75,68) : Color.rgb(232,240,233),8)); LinearLayout.LayoutParams photoParams=new LinearLayout.LayoutParams(dp(48),dp(48)); photoParams.setMargins(dp(4),0,dp(4),0); row.addView(photo,photoParams); loadImage(item.name,photo);
            CheckBox check = new CheckBox(this); check.setButtonTintList(android.content.res.ColorStateList.valueOf(GREEN)); check.setChecked(item.done); check.setText(item.name); check.setTextSize(16+fontOffset); check.setTextColor(item.done ? (dark ? Color.rgb(160,174,165) : Color.rgb(145,155,148)) : (dark ? Color.WHITE : Color.rgb(32,48,39))); check.setPaintFlags(item.done ? check.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG : check.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG); row.addView(check,new LinearLayout.LayoutParams(0,-2,1));
            check.setOnCheckedChangeListener((b,checked)-> { item.done=checked; save(); render(); });
            Button edit=button("수정",Color.TRANSPARENT,GREEN); edit.setTextSize(13+fontOffset); edit.setContentDescription(item.name + " 수정"); row.addView(edit); edit.setOnClickListener(v -> editItem(item));
            Button delete=button("삭제",Color.TRANSPARENT,Color.rgb(131,143,134)); delete.setTextSize(13+fontOffset); delete.setContentDescription(item.name + " 삭제"); row.addView(delete); delete.setOnClickListener(v -> { items.remove(item); save(); render(); });
        }
    }
    private static String today() { Calendar c=Calendar.getInstance(); return String.format(Locale.US,"%04d-%02d-%02d",c.get(Calendar.YEAR),c.get(Calendar.MONTH)+1,c.get(Calendar.DAY_OF_MONTH)); }
    private void loadAll() { try { dates=new JSONObject(getSharedPreferences("shopping",MODE_PRIVATE).getString("dates","{}")); if(dates.length()==0) { String old=getSharedPreferences("shopping",MODE_PRIVATE).getString("items",null); if(old!=null) dates.put(today(),new JSONArray(old)); } } catch(Exception e) { Toast.makeText(this,"목록을 불러오지 못했어요",Toast.LENGTH_LONG).show(); } }
    private void save() { try { JSONArray a=new JSONArray(); for(Item i:items) { JSONObject o=new JSONObject(); o.put("name",i.name); o.put("done",i.done); a.put(o); } dates.put(date,a); getSharedPreferences("shopping",MODE_PRIVATE).edit().putString("dates",dates.toString()).apply(); } catch(Exception e) { Toast.makeText(this,"목록을 저장하지 못했어요",Toast.LENGTH_SHORT).show(); } }
    private void load() { items.clear(); try { JSONArray a=dates.optJSONArray(date); if(a!=null) for(int i=0;i<a.length();i++) { JSONObject o=a.getJSONObject(i); items.add(new Item(o.getString("name"),o.optBoolean("done"))); } } catch(Exception e) { Toast.makeText(this,"날짜별 목록을 불러오지 못했어요",Toast.LENGTH_LONG).show(); } }
    private void pasteList() { ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE); if(!clipboard.hasPrimaryClip() || clipboard.getPrimaryClip().getItemCount()==0) { Toast.makeText(this,"복사된 텍스트가 없어요",Toast.LENGTH_SHORT).show(); return; } CharSequence value=clipboard.getPrimaryClip().getItemAt(0).coerceToText(this); int count=addNames(value.toString()); save(); render(); Toast.makeText(this,count+"개 항목을 추가했어요",Toast.LENGTH_SHORT).show(); }
    @Override protected void onActivityResult(int request,int result,Intent data) { super.onActivityResult(request,result,data); if(result!=RESULT_OK || data==null || data.getData()==null) return; Uri uri=data.getData(); if(request==1) { try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")) { if(out==null) throw new Exception(); out.write(dates.toString().getBytes("UTF-8")); Toast.makeText(this,"목록을 내보냈어요",Toast.LENGTH_SHORT).show(); } catch(Exception e) { Toast.makeText(this,"내보내기에 실패했어요",Toast.LENGTH_LONG).show(); } } else if(request==2) { new AlertDialog.Builder(this).setMessage("파일의 목록으로 현재 기기의 모든 날짜별 목록을 바꿀까요? 먼저 내보내기로 백업할 수 있어요.").setNegativeButton("취소",null).setPositiveButton("불러오기",(dialog,which)-> { try(InputStream in=getContentResolver().openInputStream(uri)) { if(in==null) throw new Exception(); java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream(); byte[] buf=new byte[4096]; int n; while((n=in.read(buf))!=-1) { bytes.write(buf,0,n); if(bytes.size()>5000000) throw new Exception(); } JSONObject incoming=new JSONObject(bytes.toString("UTF-8")); java.util.Iterator<String> keys=incoming.keys(); while(keys.hasNext()) { String key=keys.next(); if(!key.matches("\\d{4}-\\d{2}-\\d{2}")) throw new Exception(); JSONArray arr=incoming.getJSONArray(key); for(int j=0;j<arr.length();j++) { JSONObject item=arr.getJSONObject(j); item.getString("name"); item.getBoolean("done"); } } dates=incoming; getSharedPreferences("shopping",MODE_PRIVATE).edit().putString("dates",dates.toString()).apply(); load(); render(); Toast.makeText(this,"목록을 불러왔어요",Toast.LENGTH_SHORT).show(); } catch(Exception e) { Toast.makeText(this,"올바른 목록 파일을 불러오지 못했어요",Toast.LENGTH_LONG).show(); } }).show(); } }
    private int category(String name) {
        String n=name.toLowerCase(Locale.ROOT).replace(" ","");
        String[][] words={
            {"냉동","아이스크림","아이스바","냉면","만두","핫도그"},
            {"채소","야채","상추","양배추","배추","무","당근","양파","대파","쪽파","마늘","감자","고구마","오이","호박","가지","브로콜리","시금치","깻잎","버섯","고추","파프리카","콩나물","숙주","토마토"},
            {"과일","사과","배","귤","오렌지","바나나","딸기","포도","수박","참외","키위","복숭아","망고","레몬","블루베리"},
            {"소고기","쇠고기","돼지고기","삼겹살","목살","닭고기","닭가슴살","닭다리","오리고기","육류","불고기","다짐육","스테이크","갈비","베이컨","햄","소시지"},
            {"생선","고등어","연어","참치회","광어","새우","오징어","조개","굴","해산물","멸치","김","미역","다시마","꽃게"},
            {"우유","치즈","요거트","요구르트","버터","생크림","계란","달걀","두부"},
            {"쌀","밥","빵","식빵","국수","라면","파스타","떡","시리얼","밀가루","오트밀"},
            {"과자","초콜릿","초콜렛","사탕","젤리","쿠키","견과","팝콘"},
            {"물","생수","주스","쥬스","음료","커피","차","탄산","콜라","사이다","맥주","소주","와인"},
            {"간장","고추장","된장","소금","설탕","식초","식용유","참기름","올리브유","소스","케첩","마요네즈","후추","조미료","카레"},
            {"휴지","세제","비누","샴푸","치약","칫솔","수세미","물티슈","쓰레기봉투","키친타월","화장지","마스크"}
        };
        for(int i=0;i<words.length;i++) for(String word:words[i]) if(n.contains(word)) return i; return words.length;
    }
    private String categoryName(int category) { String[] names={"냉동식품","채소","과일","육류","수산물","유제품 · 달걀 · 두부","곡류 · 빵 · 면","간식","음료","양념 · 소스","생활용품","기타"}; return names[category]; }
    private void loadImage(String name, ImageView view) {
        view.setTag(name); Bitmap cached=imageCache.get(name); if(cached!=null) { view.setImageBitmap(cached); return; }
        view.setImageResource(android.R.drawable.ic_menu_gallery);
        if(imageRequests.contains(name) || imageUnavailable.contains(name)) return; imageRequests.add(name);
        imageExecutor.execute(() -> { Bitmap result=null; HttpURLConnection connection=null; try {
            String query="https://world.openfoodfacts.org/cgi/search.pl?search_terms="+URLEncoder.encode(name,"UTF-8")+"&search_simple=1&action=process&json=1&page_size=5&fields=product_name,image_front_small_url,image_small_url";
            connection=(HttpURLConnection)new URL(query).openConnection(); connection.setConnectTimeout(6000); connection.setReadTimeout(6000); connection.setRequestProperty("User-Agent","ShoppingChecklist/1.0 (Android)");
            if(connection.getResponseCode()!=200) throw new Exception(); java.io.ByteArrayOutputStream data=new java.io.ByteArrayOutputStream(); try(InputStream in=connection.getInputStream()) { byte[] buf=new byte[4096]; int n; while((n=in.read(buf))!=-1) { data.write(buf,0,n); if(data.size()>1000000) throw new Exception(); } } JSONObject response=new JSONObject(data.toString("UTF-8")); connection.disconnect(); connection=null;
            JSONArray products=response.optJSONArray("products"); if(products!=null) for(int i=0;i<products.length();i++) { JSONObject product=products.optJSONObject(i); if(product==null) continue; String productName=product.optString("product_name","").toLowerCase(Locale.ROOT); if(!productName.contains(name.toLowerCase(Locale.ROOT))) continue; String url=product.optString("image_front_small_url",product.optString("image_small_url","")); if(!url.startsWith("https://")) continue; connection=(HttpURLConnection)new URL(url).openConnection(); connection.setConnectTimeout(6000); connection.setReadTimeout(6000); if(connection.getResponseCode()==200) try(InputStream image=connection.getInputStream()) { result=BitmapFactory.decodeStream(image); } break; }
        } catch(Exception ignored) { } finally { if(connection!=null) connection.disconnect(); } Bitmap found=result; mainHandler.post(() -> { imageRequests.remove(name); if(found!=null) { imageCache.put(name,found); if(!isFinishing()) render(); } else imageUnavailable.add(name); }); });
    }
    @Override protected void onDestroy() { imageExecutor.shutdownNow(); super.onDestroy(); }
    private void changeFont(int delta) { int next=Math.max(-2,Math.min(6,fontOffset+delta)); if(next==fontOffset) { Toast.makeText(this,"글자 크기 조절 한도에 도달했어요",Toast.LENGTH_SHORT).show(); return; } getSharedPreferences("shopping",MODE_PRIVATE).edit().putInt("fontOffset",next).apply(); recreate(); }
    private int dp(int n) { return (int)(n*getResources().getDisplayMetrics().density+0.5f); }
    private GradientDrawable bg(int color,int radius) { GradientDrawable d=new GradientDrawable(); d.setColor(dark && color==Color.WHITE ? Color.rgb(48,58,52) : color); d.setCornerRadius(dp(radius)); return d; }
    private TextView text(String value,int size,int color) { TextView t=new TextView(this); t.setText(value); t.setTextSize(size+fontOffset); t.setTextColor(dark ? (color==GREEN ? Color.rgb(139,212,171) : Color.rgb(225,234,227)) : color); return t; }
    private Button button(String value,int background,int color) { Button b=new Button(this); b.setText(value); b.setTextColor(dark && background==Color.TRANSPARENT ? Color.rgb(139,212,171) : color); b.setTextSize(14+fontOffset); b.setAllCaps(false); b.setBackground(bg(background,12)); return b; }
}
