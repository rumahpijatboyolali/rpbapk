package id.rumahpijatboyolali.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String API_URL = "https://script.google.com/macros/s/AKfycbwk9OOgouAIw1scYBqg3sDHWeoKdHY1t8GBSJER1zl10eJlVQrFQk-CW9iKUCl4GuDAeg/exec";
    // Palet mengikuti dashboard web Rumah Pijat Boyolali.
    private static final int INK = Color.rgb(42, 33, 27), BROWN = Color.rgb(33, 25, 20);
    private static final int GOLD = Color.rgb(183, 138, 74), CREAM = Color.rgb(247, 242, 233), PAPER = 0xFFFFFDF9;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private LinearLayout root, content;
    private String token = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BROWN); getWindow().setNavigationBarColor(BROWN);
        token = state == null ? "" : state.getString("adminToken", "");
        if (token.isEmpty()) showLogin(); else showHome();
    }

    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density); }
    private GradientDrawable shape(int color, int radius, int strokeColor) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius));
        if (strokeColor != 0) d.setStroke(dp(1), strokeColor);
        return d;
    }
    private JSONObject obj(String... pairs) {
        JSONObject o = new JSONObject();
        try { for (int i=0;i+1<pairs.length;i+=2) o.put(pairs[i], pairs[i+1]); } catch(Exception ignored) {}
        return o;
    }
    private void request(String action, JSONObject data, ApiCallback cb) {
        JSONObject body = new JSONObject();
        try { body.put("action", action); body.put("token", token); body.put("data", data == null ? new JSONObject() : data); } catch(Exception ignored) {}
        io.execute(() -> {
            JSONObject response;
            try { response = post(body); } catch(Exception e) { response = new JSONObject(); try { response.put("success", false); response.put("message", e.getMessage()); } catch(Exception ignored) {} }
            final JSONObject out = response;
            runOnUiThread(() -> cb.done(out));
        });
    }
    private JSONObject post(JSONObject body) throws Exception {
        URL url = new URL(API_URL);
        String method = "POST";
        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        for (int hop = 0; hop < 6; hop++) {
            HttpURLConnection c = (HttpURLConnection)url.openConnection();
            c.setRequestMethod(method); c.setConnectTimeout(20000); c.setReadTimeout(30000);
            c.setInstanceFollowRedirects(false); c.setRequestProperty("Accept", "application/json");
            if ("POST".equals(method)) {
                c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream os = c.getOutputStream()) { os.write(payload); }
            }
            int code = c.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String location = c.getHeaderField("Location"); c.disconnect();
                if (location == null || location.trim().isEmpty()) throw new Exception("Server mengalihkan respons tanpa alamat tujuan.");
                url = new URL(url, location);
                if (code == 301 || code == 302 || code == 303) method = "GET";
                continue;
            }
            try {
                java.io.InputStream stream = code >= 400 ? c.getErrorStream() : c.getInputStream();
                if (stream == null) throw new Exception("Server tidak mengirim isi respons (HTTP " + code + ").");
                StringBuilder s = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line; while ((line = br.readLine()) != null) s.append(line);
                }
                String responseText = s.toString().trim();
                if (!responseText.startsWith("{")) {
                    throw new Exception("Server mengirim halaman HTML, bukan JSON (HTTP " + code + "). Pastikan APK terbaru dan URL Apps Script sudah benar.");
                }
                return new JSONObject(responseText);
            } finally { c.disconnect(); }
        }
        throw new Exception("Server terlalu banyak mengalihkan respons. Coba lagi beberapa saat.");
    }
    private interface ApiCallback { void done(JSONObject response); }
    private JSONObject result(JSONObject r) { return r.optJSONObject("result"); }
    private boolean ok(JSONObject r) { return r.optBoolean("success", false); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private void base(String title) {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(CREAM);
        LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setPadding(dp(18),dp(15),dp(18),dp(15)); bar.setBackgroundColor(BROWN);
        TextView brand = text("RUMAH PIJAT\nBOYOLALI", 17, Color.WHITE, true); brand.setLetterSpacing(.045f); brand.setLineSpacing(0, 1.05f); bar.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView page = text(title.toUpperCase(Locale.ROOT),11,0xFFE0C798,true); page.setGravity(Gravity.CENTER_VERTICAL); bar.addView(page);
        root.addView(bar);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        int widthDp = getResources().getConfiguration().screenWidthDp;
        int sidePadding = widthDp >= 600 ? 28 : (widthDp <= 360 ? 14 : 18);
        content.setPadding(dp(sidePadding),dp(20),dp(sidePadding),dp(28)); scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
    }
    private TextView text(String s,int size,int color,boolean bold) { TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t; }
    private EditText field(String hint) { EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(15);e.setHint(hint);e.setTextColor(INK);e.setHintTextColor(0xFF8B8177);e.setPadding(dp(14),dp(11),dp(14),dp(11));e.setBackground(shape(PAPER,12,0xFFEBE3D8)); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);content.addView(e,p);return e; }
    private Button button(String label, boolean primary, View.OnClickListener click) { Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(15);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setMinHeight(dp(48));b.setPadding(dp(16),dp(9),dp(16),dp(9));b.setTextColor(primary?Color.WHITE:INK);b.setBackground(shape(primary?GOLD:0xFFFFFDF9,12,primary?0:0xFFEBE3D8));b.setElevation(dp(2));b.setOnClickListener(click);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);content.addView(b,p);return b; }
    private void gap(int h) { View v=new View(this);content.addView(v,new LinearLayout.LayoutParams(1,dp(h))); }
    private void heading(String s) { TextView t=text(s,21,INK,true);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(14);content.addView(t,p); }
    private void showLogin() {
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF2D160C,0xFF6F431F,0xFFC49A5A}));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout frame=new LinearLayout(this);frame.setGravity(Gravity.CENTER);frame.setPadding(dp(20),dp(24),dp(20),dp(24));scroll.addView(frame,new ScrollView.LayoutParams(-1,-1));
        LinearLayout loginCard=new LinearLayout(this);loginCard.setOrientation(LinearLayout.VERTICAL);loginCard.setPadding(dp(24),dp(26),dp(24),dp(22));loginCard.setBackground(shape(PAPER,24,0));loginCard.setElevation(dp(12));int maxWidth=getResources().getConfiguration().screenWidthDp>=600?440:-1;frame.addView(loginCard,new LinearLayout.LayoutParams(maxWidth<0?-1:dp(maxWidth),-2));content=loginCard;
        TextView brand=text("RUMAH PIJAT BOYOLALI",12,GOLD,true);brand.setLetterSpacing(.1f);brand.setGravity(Gravity.CENTER);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.bottomMargin=dp(12);content.addView(brand,bp);
        TextView title=text("Masuk ke panel admin",23,INK,true);title.setGravity(Gravity.CENTER);content.addView(title);
        TextView note=text("Silakan masuk untuk mengakses dashboard",14,0xFF8B8177,false);note.setGravity(Gravity.CENTER);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.topMargin=dp(7);np.bottomMargin=dp(16);content.addView(note,np);
        EditText username=field("Username"); EditText password=field("Password");password.setInputType(129);
        button("Masuk",true,v->{ JSONObject d=obj("username",username.getText().toString().trim(),"password",password.getText().toString());request("login",d,r->{JSONObject x=result(r);if(ok(r)&&x!=null&&x.optBoolean("success")){token=x.optString("token");showHome();}else toast(x==null?r.optString("message","Login gagal"):x.optString("message","Login gagal"));}); });
        TextView foot=text("Sesi berakhir otomatis setelah tidak aktif.",12,0xFF8B8177,false);foot.setGravity(Gravity.CENTER);LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,-2);fp.topMargin=dp(8);content.addView(foot,fp);
    }
    private void showHome() {
        base("Dashboard");
        heading("Selamat datang 👋");
        TextView intro=text("Pantau penjualan dan kelola transaksi Rumah Pijat Boyolali dari satu halaman.",14,0xFF8B8177,false);
        intro.setLineSpacing(dp(3),1f); LinearLayout.LayoutParams introP=new LinearLayout.LayoutParams(-1,-2);introP.bottomMargin=dp(18);content.addView(intro,introP);
        LinearLayout cards=new LinearLayout(this);cards.setOrientation(LinearLayout.VERTICAL);content.addView(cards);
        TextView status=text("Memuat ringkasan…",14,0xFF8B8177,false);status.setPadding(dp(4),dp(10),dp(4),dp(18));cards.addView(status);
        request("dashboard",new JSONObject(),r->{if(!r.optBoolean("success")){status.setText(r.optString("message","Gagal memuat data"));if(status.getText().toString().toLowerCase(Locale.ROOT).contains("login"))showLogin();return;}JSONObject d=result(r);cards.removeAllViews();String[][] metrics={{"Pijat Hari Ini",money(d.optDouble("todayTotal")),d.optInt("todayCount")+" transaksi pijat"},{"Pemasukan Lain Hari Ini",money(d.optDouble("todayOtherIncome")),"Di luar pemasukan pijat"},{"Pengeluaran Hari Ini",money(d.optDouble("todayExpense")),"Total pengeluaran harian"},{"Bersih Hari Ini",money(d.optDouble("todayNet")),"Pemasukan dikurangi pengeluaran"},{"Pendapatan Bulan Ini",money(d.optDouble("monthTotal")+d.optDouble("monthOtherIncome")),"Pijat + pemasukan lain"},{"Bersih Bulan Ini",money(d.optDouble("monthNet")),"Pendapatan dikurangi pengeluaran"}};int widthDp=getResources().getConfiguration().screenWidthDp;int columns=widthDp>360?2:1;for(int i=0;i<metrics.length;i++){if(i%columns==0){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);cards.addView(row,new LinearLayout.LayoutParams(-1,-2));}LinearLayout row=(LinearLayout)cards.getChildAt(cards.getChildCount()-1);View item=cardView(metrics[i][0],metrics[i][1],metrics[i][2]);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.setMargins(dp(i%columns==0?0:5),dp(0),dp(i%columns==columns-1?0:5),dp(10));row.addView(item,cp);if(columns==2&&i==metrics.length-1){View spacer=new View(this);row.addView(spacer,new LinearLayout.LayoutParams(0,1,1));}}});
        gap(2); heading("Menu utama");
        button("Jadwal pijat",true,v->showSchedules());button("Tambah transaksi",false,v->showAddTransaction());button("Daftar transaksi",false,v->showTransactions());
        button("Keluar",false,v->{request("logout",new JSONObject(),r->{token="";showLogin();});});
    }
    private View cardView(String title,String value,String sub) { LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(13),dp(14),dp(13),dp(14));box.setBackground(shape(PAPER,16,0xFFEBE3D8));box.setElevation(dp(2));TextView label=text(title,12,0xFF8B8177,true);box.addView(label);TextView amount=text(value,22,INK,true);amount.setSingleLine(true);amount.setTextSize(getResources().getConfiguration().screenWidthDp<=380?17:21);amount.setLetterSpacing(-.02f);amount.setIncludeFontPadding(false);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);ap.topMargin=dp(10);box.addView(amount,ap);TextView secondary=text(sub,11,0xFF8B8177,false);secondary.setLineSpacing(dp(2),1f);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(6);box.addView(secondary,sp);return box; }
    private String money(double n) { return NumberFormat.getCurrencyInstance(new Locale("id","ID")).format(n).replace(",00", ""); }

    private void showSchedules() {
        base("Jadwal");heading("Jadwal pijat");
        EditText name=field("Nama pelanggan");EditText date=field("Tanggal (YYYY-MM-DD)");date.setText(today());EditText time=field("Jam (HH:MM)");EditText treatment=field("Treatment: HC atau OT");treatment.setText("HC");
        button("Simpan jadwal",true,v->{JSONObject d=obj("nama",name.getText().toString(),"tanggal",date.getText().toString(),"jam",time.getText().toString(),"treatment",treatment.getText().toString());request("saveSchedule",d,r->{JSONObject x=result(r);if(r.optBoolean("success")&&(x==null||x.optBoolean("success",true))){toast(x==null?"Jadwal disimpan":x.optString("message"));showSchedules();}else toast(x==null?r.optString("message"):x.optString("message"));});});
        gap(8);heading("Jadwal minggu ini");TextView status=text("Memuat jadwal…",14,INK,false);content.addView(status);
        Calendar cal=Calendar.getInstance();cal.setFirstDayOfWeek(Calendar.MONDAY);cal.set(Calendar.DAY_OF_WEEK,Calendar.MONDAY);String start=fmt(cal);cal.add(Calendar.DAY_OF_MONTH,6);String end=fmt(cal);
        request("schedules",obj("startDate",start,"endDate",end),r->{if(!r.optBoolean("success")){status.setText(r.optString("message"));return;}JSONArray rows=r.optJSONArray("result");content.removeView(status);if(rows==null||rows.length()==0){content.addView(text("Belum ada jadwal minggu ini.",14,0xFF76695D,false));return;}for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null)scheduleCard(row);}});
        button("Kembali",false,v->showHome());
    }
    private void scheduleCard(JSONObject row) { LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(15),dp(14),dp(15),dp(10));box.setBackground(shape(PAPER,15,0xFFEBE3D8));box.setElevation(dp(1));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);content.addView(box,p);box.addView(text(row.optString("tanggal")+"  ·  "+row.optString("jam"),12,GOLD,true));TextView name=text(row.optString("nama"),17,INK,true);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.topMargin=dp(4);box.addView(name,np);box.addView(text(row.optString("treatment","HC").toUpperCase(Locale.ROOT),12,0xFF76695D,false));Button del=new Button(this);del.setText("Hapus jadwal");del.setAllCaps(false);del.setTextColor(0xFFB83A32);del.setBackground(shape(PAPER,10,0xFFEBE3D8));del.setOnClickListener(v->new AlertDialog.Builder(this).setMessage("Hapus jadwal ini?").setNegativeButton("Batal",null).setPositiveButton("Hapus",(a,b)->request("deleteSchedule",obj("id",row.optString("id"),"tanggal",row.optString("tanggal")),r->{toast(r.optString("message","Selesai"));showSchedules();})).show());box.addView(del); }

    private void showAddTransaction() {
        base("Transaksi");heading("Tambah transaksi");EditText name=field("Nama pelanggan");EditText date=field("Tanggal (YYYY-MM-DD)");date.setText(today());EditText amount=field("Nominal (contoh 100000)");amount.setInputType(2);
        button("Simpan transaksi",true,v->{JSONObject d=obj("nama",name.getText().toString(),"tanggal",date.getText().toString(),"nominal",amount.getText().toString());request("addTransaction",d,r->{if(r.optBoolean("success")){JSONObject x=result(r);toast(x==null?"Transaksi tersimpan":x.optString("message"));showHome();}else toast(r.optString("message","Gagal menyimpan"));});});button("Kembali",false,v->showHome());
    }
    private void showTransactions() {
        base("Transaksi");heading("Transaksi terbaru");TextView status=text("Memuat transaksi…",14,INK,false);content.addView(status);
        request("transactions",new JSONObject(),r->{if(!r.optBoolean("success")){status.setText(r.optString("message"));return;}JSONArray rows=r.optJSONArray("result");content.removeView(status);if(rows==null||rows.length()==0){content.addView(text("Belum ada transaksi.",14,0xFF76695D,false));return;}int limit=Math.min(rows.length(),60);for(int i=0;i<limit;i++){JSONObject row=rows.optJSONObject(i);if(row==null)continue;LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(15),dp(13),dp(15),dp(13));box.setBackground(shape(PAPER,14,0xFFEBE3D8));box.setElevation(dp(1));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(9);content.addView(box,p);box.addView(text(row.optString("tanggal"),12,GOLD,true));TextView name=text(row.optString("nama"),16,INK,true);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.topMargin=dp(4);box.addView(name,np);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);ap.topMargin=dp(3);box.addView(text(money(row.optDouble("nominal")),14,INK,false),ap);}});
        button("Kembali",false,v->showHome());
    }

    private String fmt(Calendar calendar) { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.getTime()); }
    private String today() { return fmt(Calendar.getInstance()); }

    @Override protected void onSaveInstanceState(Bundle state){state.putString("adminToken",token);super.onSaveInstanceState(state);}
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
