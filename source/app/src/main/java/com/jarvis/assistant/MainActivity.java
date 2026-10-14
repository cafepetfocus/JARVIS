package com.jarvis.assistant;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.os.Build;
import android.Manifest;
import android.content.pm.PackageManager;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.database.Cursor;
import android.provider.DocumentsContract;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    // Thème sombre anthracite avec accent violet discret.
    private final int bg = Color.rgb(15, 15, 16), panel = Color.rgb(31, 31, 33), mint = Color.rgb(119, 100, 133), white = Color.rgb(236, 236, 236), muted = Color.rgb(160, 160, 160);
    private android.content.SharedPreferences prefs;
    private LinearLayout root, messages;
    private ScrollView scroll;
    private EditText input;
    private TextView status;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private JSONArray history = new JSONArray();
    private static final int REQUEST_PROJECT_TREE = 7410;
    private Uri projectTreeUri;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        prefs = getSharedPreferences("jarvis", MODE_PRIVATE);
        try { history = new JSONArray(prefs.getString("history", "[]")); } catch (Exception ignored) {}
        createReminderChannel();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7401);
        buildUi();
        addMessage("JARVIS", "Bonjour. Je suis ta première version de JARVIS. Configure un fournisseur compatible avec l'API Chat Completions dans ⚙ Réglages pour commencer. Ta mémoire et ton historique restent sur ce téléphone.", false);
        renderHistory();
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private GradientDrawable shape(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); return d; }
    private TextView text(String s, int size, int color, boolean bold) { TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); t.setGravity(Gravity.CENTER_VERTICAL); return t; }
    private void buildUi() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(bg); root.setPadding(dp(16), dp(14), dp(16), dp(12)); setContentView(root);
        // Android 15 / targetSdk 35 uses edge-to-edge: reserve the real system-bar areas.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int left = insets.getSystemWindowInsetLeft();
            int top = insets.getSystemWindowInsetTop();
            int right = insets.getSystemWindowInsetRight();
            int bottom = insets.getSystemWindowInsetBottom();
            v.setPadding(left + dp(16), top + dp(10), right + dp(16), bottom + dp(12));
            return insets;
        });
        root.setFitsSystemWindows(false);
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(0, 0, 0, 12);
        TextView logo = text("✦", 28, mint, true); header.addView(logo, new LinearLayout.LayoutParams(dp(44), dp(48)));
        LinearLayout brand = new LinearLayout(this); brand.setOrientation(LinearLayout.VERTICAL); brand.addView(text("J.A.R.V.I.S.", 20, white, true)); brand.addView(text("ASSISTANT PERSONNEL · V0.1", 10, muted, false)); header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        Button code = button("⌘ Code", panel, white); code.setOnClickListener(v -> showCodeTools()); header.addView(code);
        Button settings = button("⚙ Réglages", panel, white); settings.setOnClickListener(v -> showSettings()); header.addView(settings); root.addView(header);
        status = text("● Prêt · modèle non configuré", 12, muted, false); status.setPadding(8, 6, 8, 12); root.addView(status);
        scroll = new ScrollView(this); scroll.setFillViewport(true); messages = new LinearLayout(this); messages.setOrientation(LinearLayout.VERTICAL); messages.setPadding(0, 4, 0, 12); scroll.addView(messages); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout composer = new LinearLayout(this); composer.setGravity(Gravity.CENTER_VERTICAL); composer.setPadding(0, 10, 0, 8);
        // Le pied de page est placé au-dessus du champ : celui-ci reste plus haut et accessible.
        TextView foot = text("Prototype · configure ton propre fournisseur IA", 10, muted, false); foot.setGravity(Gravity.CENTER); foot.setPadding(0, 4, 0, 4); root.addView(foot);
        input = new EditText(this); input.setSingleLine(false); input.setMinLines(1); input.setMaxLines(4); input.setTextColor(white); input.setHintTextColor(muted); input.setHint("Écris à JARVIS…"); input.setPadding(dp(14), dp(12), dp(14), dp(12)); input.setMinHeight(dp(48)); input.setFocusable(true); input.setFocusableInTouchMode(true); input.setClickable(true); input.setBackground(shape(panel, 22)); composer.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
        Button send = button("➤", mint, bg); send.setOnClickListener(v -> sendMessage()); LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(52), dp(48)); sp.setMargins(dp(8), 0, 0, 0); composer.addView(send, sp); root.addView(composer);
    }
    private Button button(String s, int color, int fg) { Button b = new Button(this); b.setText(s); b.setTextColor(fg); b.setTextSize(12); b.setAllCaps(false); b.setBackground(shape(color, 18)); b.setPadding(12, 4, 12, 4); return b; }

    private void addMessage(String who, String body, boolean user) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(14, 10, 14, 11); card.setBackground(shape(user ? Color.rgb(49, 43, 54) : panel, 18));
        TextView label = text(who, 11, user ? mint : muted, true); card.addView(label);
        TextView content = text(body, 15, white, false); content.setGravity(Gravity.TOP); content.setPadding(0, 5, 0, 0); card.addView(content);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(0, 0, 0, 10); messages.addView(card, p); scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }
    private void renderHistory() { messages.removeAllViews(); for (int i=0;i<history.length();i++) { JSONObject m=history.optJSONObject(i); if(m!=null) addMessage(m.optString("role").equals("user")?"TOI":"JARVIS",m.optString("content"),m.optString("role").equals("user")); } }
    private void saveHistory() { prefs.edit().putString("history", history.toString()).apply(); }
    private void saveMessage(String role, String content) { JSONObject m=new JSONObject(); try { m.put("role",role); m.put("content",content); history.put(m); saveHistory(); } catch(Exception ignored){} }
    private void sendMessage() {
        String prompt=input.getText().toString().trim(); if(prompt.isEmpty()) return; input.setText(""); ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);
        if(prompt.startsWith("/rappel ")) {
            handleReminder(prompt);
            return;
        }
        if(prompt.equalsIgnoreCase("/aide")) {
            String reply="Commandes disponibles :\n/remember texte — enregistrer un souvenir local.\n/rappel JJ/MM/AAAA HH:MM | texte — programmer une notification.\n/aide — afficher ces commandes.";
            addMessage("TOI",prompt,true); saveMessage("user",prompt); addMessage("JARVIS",reply,false); saveMessage("assistant",reply); return;
        }
        if(prompt.startsWith("/remember ")) { String mem=prompt.substring(9).trim(); JSONArray a; try { a=new JSONArray(prefs.getString("memory","[]")); } catch(Exception e){a=new JSONArray();} a.put(mem); prefs.edit().putString("memory",a.toString()).apply(); addMessage("TOI",prompt,true); saveMessage("user",prompt); String reply="C'est enregistré dans ta mémoire locale. Tu peux la consulter ou la supprimer dans ⚙ Réglages."; addMessage("JARVIS",reply,false); saveMessage("assistant",reply); return; }
        addMessage("TOI",prompt,true); saveMessage("user",prompt);
        String endpoint=prefs.getString("endpoint","").trim(), key=prefs.getString("key","").trim(), model=prefs.getString("model","").trim();
        if(endpoint.isEmpty() || model.isEmpty()) { String reply="Le fournisseur IA n'est pas encore configuré. Ouvre ⚙ Réglages et indique l'URL API, le modèle et éventuellement une clé API."; addMessage("JARVIS",reply,false); saveMessage("assistant",reply); return; }
        status.setText("● JARVIS réfléchit…"); executor.execute(() -> { try {
            JSONArray msgs=new JSONArray(); JSONObject sys=new JSONObject(); sys.put("role","system"); sys.put("content",prefs.getString("personality","Tu es JARVIS, un assistant personnel utile, honnête et concis. Réponds en français sauf demande contraire.")+"\nMémoire utilisateur : "+prefs.getString("memory","[]")); msgs.put(sys);
            JSONArray hist=new JSONArray(prefs.getString("history","[]")); int start=Math.max(0,hist.length()-20); for(int i=start;i<hist.length();i++){JSONObject h=hist.optJSONObject(i);if(h!=null)msgs.put(h);}
            JSONObject payload=new JSONObject(); payload.put("model",model); payload.put("messages",msgs); payload.put("temperature",0.7);
            HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection(); c.setRequestMethod("POST"); c.setConnectTimeout(15000); c.setReadTimeout(60000); c.setDoOutput(true); c.setRequestProperty("Content-Type","application/json"); if(!key.isEmpty())c.setRequestProperty("Authorization","Bearer "+key);
            try(OutputStream os=c.getOutputStream()){os.write(payload.toString().getBytes(StandardCharsets.UTF_8));}
            int code=c.getResponseCode(); InputStream is=code>=200&&code<300?c.getInputStream():c.getErrorStream(); ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buf=new byte[4096]; int n; while((n=is.read(buf))!=-1)out.write(buf,0,n); String raw=out.toString("UTF-8"); if(code<200||code>=300)throw new Exception("HTTP "+code+": "+raw.substring(0,Math.min(raw.length(),400)));
            JSONObject data=new JSONObject(raw); String answer=data.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content","Réponse vide"); runOnUiThread(()->{addMessage("JARVIS",answer,false);saveMessage("assistant",answer);status.setText("● Connecté · réponse reçue");}); c.disconnect();
        } catch(Exception e){String err="Impossible d'obtenir une réponse : "+e.getMessage()+"\nVérifie l'URL, le modèle, la clé API et la connexion.";runOnUiThread(()->{addMessage("JARVIS",err,false);saveMessage("assistant",err);status.setText("● Erreur de connexion");});}});
    }


    // Outils de modification du code : accès uniquement au dossier choisi explicitement par l'utilisateur.
    private void showCodeTools() {
        String saved = prefs.getString("projectTreeUri", "");
        if (projectTreeUri == null && !saved.isEmpty()) try { projectTreeUri = Uri.parse(saved); } catch (Exception ignored) {}
        String selected = projectTreeUri == null ? "Aucun dossier de projet sélectionné" : "Dossier de projet autorisé : " + projectTreeUri;
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18), dp(12), dp(18), dp(8)); box.setBackgroundColor(bg);
        TextView title = text("AUTO-MODIFICATION · EXPÉRIMENTAL", 18, mint, true); box.addView(title);
        TextView info = text(selected + "\n\nChoisis la racine du projet JARVIS. L'outil pourra lire les fichiers source compatibles et proposer des modifications. Chaque écriture demandera une confirmation. Fais une copie du projet avant de commencer.", 13, white, false); info.setPadding(0, dp(10), 0, dp(12)); box.addView(info);
        Button choose = button("Choisir / changer le dossier", panel, white); choose.setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION); startActivityForResult(i, REQUEST_PROJECT_TREE); }); box.addView(choose);
        Button browse = button("Parcourir les fichiers du projet", mint, bg); browse.setOnClickListener(v -> { if (projectTreeUri == null) Toast.makeText(this, "Choisis d'abord le dossier du projet.", Toast.LENGTH_LONG).show(); else browseProjectFiles(); }); LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2); bp.topMargin = dp(8); box.addView(browse, bp);
        new android.app.AlertDialog.Builder(this).setView(box).setPositiveButton("Fermer", (d,w) -> {}).show();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PROJECT_TREE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(uri, flags);
                projectTreeUri = uri;
                prefs.edit().putString("projectTreeUri", uri.toString()).apply();
                Toast.makeText(this, "Dossier du projet autorisé.", Toast.LENGTH_LONG).show();
                browseProjectFiles();
            } catch (Exception e) { Toast.makeText(this, "Autorisation du dossier impossible : " + e.getMessage(), Toast.LENGTH_LONG).show(); }
        }
    }

    private static class ProjectFile { Uri uri; String name; ProjectFile(Uri u, String n) { uri=u; name=n; } }
    private boolean isEditableSource(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".java") || n.endsWith(".kt") || n.endsWith(".xml") || n.endsWith(".gradle") || n.endsWith(".properties") || n.endsWith(".md") || n.endsWith(".pro") || n.endsWith(".json") || n.endsWith(".txt");
    }
    private void browseProjectFiles() {
        final Uri selectedTree = projectTreeUri;
        if (selectedTree == null) {
            Toast.makeText(this, "Choisis d'abord le dossier du projet.", Toast.LENGTH_LONG).show();
            return;
        }
        executor.execute(() -> {
            java.util.ArrayList<ProjectFile> files = new java.util.ArrayList<>();
            try {
                String treeId = DocumentsContract.getTreeDocumentId(selectedTree);
                Uri rootDocument = DocumentsContract.buildDocumentUriUsingTree(selectedTree, treeId);
                collectProjectFiles(selectedTree, rootDocument, files, 0);
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                runOnUiThread(() -> new android.app.AlertDialog.Builder(this)
                    .setTitle("Accès au dossier impossible")
                    .setMessage(message + "\n\nVérifie que tu as sélectionné le dossier JARVIS et appuyé sur « Utiliser ce dossier ».")
                    .setPositiveButton("OK", null).show());
                return;
            }
            runOnUiThread(() -> {
                if (files.isEmpty()) {
                    new android.app.AlertDialog.Builder(this)
                        .setTitle("Aucun fichier source trouvé")
                        .setMessage("Le dossier a été autorisé, mais aucun fichier source compatible n'a été lu. Choisis la racine JARVIS contenant app, build.gradle et settings.gradle.")
                        .setPositiveButton("OK", null).show();
                    return;
                }
                String[] names = new String[files.size()];
                for (int i=0;i<files.size();i++) names[i]=files.get(i).name;
                new android.app.AlertDialog.Builder(this).setTitle("Fichiers source ("+files.size()+")")
                    .setItems(names, (d, which) -> editProjectFile(files.get(which)))
                    .setNegativeButton("Fermer", null).show();
            });
        });
    }
    private void collectProjectFiles(Uri treeUri, Uri parentDocumentUri, java.util.ArrayList<ProjectFile> out, int depth) throws Exception {
        if (depth > 12 || out.size() >= 250) return;
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getDocumentId(parentDocumentUri));
        try (Cursor c = getContentResolver().query(children, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
            if (c == null) return;
            while (c.moveToNext() && out.size() < 250) {
                String id=c.getString(0), name=c.getString(1), mime=c.getString(2);
                Uri child=DocumentsContract.buildDocumentUriUsingTree(treeUri,id);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    String low=name == null ? "" : name.toLowerCase(Locale.ROOT);
                    if (!low.equals(".git") && !low.equals("build") && !low.equals(".gradle") && !low.equals(".idea") && !low.equals("node_modules")) collectProjectFiles(treeUri, child, out, depth+1);
                } else if (name != null && isEditableSource(name)) out.add(new ProjectFile(child, name));
            }
        }
    }
    private void editProjectFile(ProjectFile file) {
        executor.execute(() -> {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (InputStream in=getContentResolver().openInputStream(file.uri)) { if(in==null) throw new IOException("Fichier inaccessible"); byte[] b=new byte[4096]; int n; while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>300000)throw new IOException("Fichier trop volumineux (>300 Ko)");} }
                String original=out.toString("UTF-8");
                runOnUiThread(() -> showSourceEditor(file, original));
            } catch(Exception e) { runOnUiThread(() -> Toast.makeText(this,"Lecture impossible : "+e.getMessage(),Toast.LENGTH_LONG).show()); }
        });
    }
    private void showSourceEditor(ProjectFile file, String original) { showSourceEditor(file, original, original); }
    private void showSourceEditor(ProjectFile file, String original, String initialText) {
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(12),dp(8),dp(12),dp(8)); box.setBackgroundColor(bg);
        TextView note=text(file.name+"\nModification expérimentale : vérifie le code avant de l'enregistrer.",12,muted,false); box.addView(note);
        EditText editor=new EditText(this); editor.setTextColor(white); editor.setHintTextColor(muted); editor.setTextSize(12); editor.setGravity(Gravity.TOP|Gravity.START); editor.setTypeface(Typeface.MONOSPACE); editor.setSingleLine(false); editor.setMinLines(12); editor.setMaxLines(24); editor.setHorizontallyScrolling(false); editor.setPadding(dp(10),dp(10),dp(10),dp(10)); editor.setBackground(shape(panel,12)); editor.setText(initialText); LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,dp(380)); ep.topMargin=dp(8); box.addView(editor,ep);
        Button aiEdit=button("Demander à l’IA de modifier ce fichier",panel,white); LinearLayout.LayoutParams aip=new LinearLayout.LayoutParams(-1,-2); aip.topMargin=dp(8); box.addView(aiEdit,0); aiEdit.setOnClickListener(v -> requestAiCodeChange(file, original));
        new android.app.AlertDialog.Builder(this).setTitle("Éditer le code").setView(box).setNegativeButton("Annuler",null).setNeutralButton("Restaurer original",(d,w)->showSourceEditor(file,original)).setPositiveButton("Prévisualiser / enregistrer",(d,w)->{
            String changed=editor.getText().toString();
            if(changed.equals(original)){Toast.makeText(this,"Aucun changement.",Toast.LENGTH_SHORT).show();return;}
            String preview="Fichier : "+file.name+"\n\nOriginal : "+original.length()+" caractères\nNouvelle version : "+changed.length()+" caractères\n\nLes modifications seront écrites dans le fichier choisi. Une sauvegarde du contenu original sera conservée dans les données privées de JARVIS. Continuer ?";
            new android.app.AlertDialog.Builder(this).setTitle("Confirmer l'écriture").setMessage(preview).setNegativeButton("Annuler",null).setPositiveButton("Écrire le fichier",(dd,ww)->writeProjectFile(file,original,changed)).show();
        }).show();
    }
    private void requestAiCodeChange(ProjectFile file, String original) {
        String endpoint=prefs.getString("endpoint","").trim(), key=prefs.getString("key","").trim(), model=prefs.getString("model","").trim();
        if(endpoint.isEmpty() || model.isEmpty()) { Toast.makeText(this,"Configure d'abord le fournisseur IA dans Réglages.",Toast.LENGTH_LONG).show(); return; }
        if(original.length()>80000) { Toast.makeText(this,"Fichier trop long pour la modification IA intégrée (limite 80 000 caractères).",Toast.LENGTH_LONG).show(); return; }
        EditText request=new EditText(this); request.setTextColor(white); request.setHintTextColor(muted); request.setHint("Décris précisément la modification souhaitée…"); request.setMinLines(3); request.setGravity(Gravity.TOP|Gravity.START); request.setPadding(dp(12),dp(10),dp(12),dp(10)); request.setBackground(shape(panel,12));
        new android.app.AlertDialog.Builder(this).setTitle("Modification par IA · "+file.name).setMessage("L'IA recevra le contenu de ce fichier et ta demande via le fournisseur configuré. N'inclus pas de secrets ou de clés API dans le code.").setView(request).setNegativeButton("Annuler",null).setPositiveButton("Générer",(d,w)->{
            String instruction=request.getText().toString().trim(); if(instruction.isEmpty()){Toast.makeText(this,"Décris la modification demandée.",Toast.LENGTH_SHORT).show();return;}
            status.setText("● L'IA prépare une modification du code…");
            executor.execute(()->{
                try {
                    JSONArray msgs=new JSONArray();
                    JSONObject sys=new JSONObject(); sys.put("role","system"); sys.put("content","Tu es un ingénieur logiciel Android expérimenté. L'utilisateur te demande de modifier UN fichier source. Retourne uniquement le contenu complet du fichier modifié, sans Markdown, sans bloc de code, sans explication ni texte avant/après. Préserve les fonctionnalités existantes, n'ajoute pas de comportement caché, n'exfiltre aucune donnée et ne désactive pas les protections. Si la demande est ambiguë ou impossible, retourne le fichier inchangé plutôt que d'inventer des API."); msgs.put(sys);
                    JSONObject usr=new JSONObject(); usr.put("role","user"); usr.put("content","Fichier : "+file.name+"\nDemande de modification :\n"+instruction+"\n\nContenu actuel du fichier :\n"+original); msgs.put(usr);
                    JSONObject payload=new JSONObject(); payload.put("model",model); payload.put("messages",msgs); payload.put("temperature",0.2);
                    HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection(); c.setRequestMethod("POST"); c.setConnectTimeout(15000); c.setReadTimeout(90000); c.setDoOutput(true); c.setRequestProperty("Content-Type","application/json"); if(!key.isEmpty())c.setRequestProperty("Authorization","Bearer "+key);
                    try(OutputStream os=c.getOutputStream()){os.write(payload.toString().getBytes(StandardCharsets.UTF_8));}
                    int code=c.getResponseCode(); InputStream is=code>=200&&code<300?c.getInputStream():c.getErrorStream(); ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buf=new byte[4096]; int n; while((n=is.read(buf))!=-1)out.write(buf,0,n); String raw=out.toString("UTF-8"); c.disconnect();
                    if(code<200||code>=300) throw new Exception("HTTP "+code+": "+raw.substring(0,Math.min(raw.length(),300)));
                    String generated=new JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content","").trim();
                    if(generated.startsWith("```")) { generated=generated.replaceFirst("^```[\\w-]*\\s*",""); generated=generated.replaceFirst("\\s*```$","").trim(); }
                    if(generated.isEmpty()) throw new Exception("Réponse vide du modèle");
                    final String result=generated;
                    runOnUiThread(()->{status.setText("● Modification générée · vérification requise"); showSourceEditor(file,original,result);});
                } catch(Exception e) { runOnUiThread(()->{status.setText("● Échec de génération"); new android.app.AlertDialog.Builder(this).setTitle("L'IA n'a pas pu modifier le fichier").setMessage(e.getMessage()).setPositiveButton("OK",null).show();}); }
            });
        }).show();
    }

    private void writeProjectFile(ProjectFile file, String original, String changed) {
        executor.execute(() -> {
            try {
                // Keep the last pre-edit version in private app storage before touching project files.
                String backupKey="sourceBackup_"+Integer.toHexString(file.uri.toString().hashCode());
                prefs.edit().putString(backupKey,original).apply();
                try(OutputStream out=getContentResolver().openOutputStream(file.uri,"wt")) { if(out==null) throw new IOException("Impossible d'ouvrir le fichier en écriture"); out.write(changed.getBytes(StandardCharsets.UTF_8)); out.flush(); }
                runOnUiThread(()->new android.app.AlertDialog.Builder(this).setTitle("Fichier modifié").setMessage(file.name+" a été enregistré. La sauvegarde de l'ancienne version est conservée dans les données privées de l'application. JARVIS n'a pas encore compilé ni testé le projet : vérifie le diff et lance une compilation avant d'installer l'APK.").setPositiveButton("OK",null).show());
            } catch(Exception e) { runOnUiThread(()->new android.app.AlertDialog.Builder(this).setTitle("Écriture échouée").setMessage(e.getMessage()+"\nLe fournisseur de fichiers peut ne pas autoriser l'écriture.").setPositiveButton("OK",null).show()); }
        });
    }

    private void createReminderChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationManager manager = (android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            manager.createNotificationChannel(new android.app.NotificationChannel(ReminderReceiver.CHANNEL_ID, "Rappels JARVIS", android.app.NotificationManager.IMPORTANCE_HIGH));
        }
    }
    private void handleReminder(String prompt) {
        addMessage("TOI",prompt,true); saveMessage("user",prompt);
        try {
            String spec=prompt.substring("/rappel ".length()).trim();
            int sep=spec.indexOf('|');
            if(sep<0) throw new IllegalArgumentException("Sépare la date et le texte avec |.");
            String dateText=spec.substring(0,sep).trim(); String title=spec.substring(sep+1).trim();
            if(title.isEmpty()) throw new IllegalArgumentException("Ajoute le texte du rappel après |.");
            SimpleDateFormat format=new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE); format.setLenient(false);
            Date date=format.parse(dateText); if(date==null || date.getTime()<=System.currentTimeMillis()) throw new IllegalArgumentException("La date doit être dans le futur.");
            int requestCode=(int)(date.getTime() ^ title.hashCode());
            Intent intent=new Intent(this, ReminderReceiver.class); intent.putExtra("title",title);
            PendingIntent pi=PendingIntent.getBroadcast(this,requestCode,intent,PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            AlarmManager alarm=(AlarmManager)getSystemService(ALARM_SERVICE);
            if(Build.VERSION.SDK_INT>=23) alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,date.getTime(),pi); else alarm.set(AlarmManager.RTC_WAKEUP,date.getTime(),pi);
            String reply="C'est programmé : "+dateText+"\nRappel : "+title+"\nJe t'enverrai une notification à cette heure. Vérifie que les notifications de JARVIS sont autorisées.";
            addMessage("JARVIS",reply,false); saveMessage("assistant",reply);
        } catch(Exception e) {
            String reply="Je n'ai pas pu créer le rappel. Format attendu : /rappel 14/10/2026 18:30 | vérifier mon compte.\nDétail : "+e.getMessage();
            addMessage("JARVIS",reply,false); saveMessage("assistant",reply);
        }
    }

    private EditText field(LinearLayout box, String label, String value, String hint, boolean secret) { TextView l=text(label,13,white,true); l.setPadding(0,10,0,3); box.addView(l); EditText e=new EditText(this); e.setTextColor(white); e.setHintTextColor(muted); e.setHint(hint); e.setText(value); e.setSingleLine(true); e.setPadding(12,8,12,8); e.setBackground(shape(panel,12)); if(secret)e.setInputType(129); box.addView(e); return e; }
    private void showSettings() {
        ScrollView sv=new ScrollView(this); LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(20,8,20,12); box.setBackgroundColor(bg); sv.addView(box);
        TextView intro=text("RÉGLAGES JARVIS",20,mint,true); box.addView(intro); TextView note=text("Compatible avec une API de type OpenAI Chat Completions. L'URL doit être l'endpoint complet, par exemple /v1/chat/completions. Évite les fournisseurs inconnus : la clé est stockée localement sur le téléphone, pas dans un coffre sécurisé.",12,muted,false); note.setPadding(0,8,0,8); box.addView(note);
        EditText endpoint=field(box,"URL de l'API",prefs.getString("endpoint",""),"https://serveur.example/v1/chat/completions",false);
        EditText model=field(box,"Nom du modèle",prefs.getString("model",""),"nom-du-modele",false);
        EditText key=field(box,"Clé API (facultative selon le fournisseur)",prefs.getString("key",""),"clé API",true);
        EditText personality=field(box,"Personnalité / instructions",prefs.getString("personality","Tu es JARVIS, un assistant personnel utile, honnête et concis. Réponds en français sauf demande contraire."),"Instructions de JARVIS",false); personality.setSingleLine(false); personality.setMinLines(3);
        TextView memoryTitle=text("MÉMOIRE LOCALE",15,mint,true); memoryTitle.setPadding(0,16,0,4); box.addView(memoryTitle);
        TextView memory=text(prefs.getString("memory","[]"),12,white,false); box.addView(memory);
        Button clearMem=button("Effacer la mémoire",panel,white); clearMem.setOnClickListener(v->{prefs.edit().putString("memory","[]").apply(); memory.setText("[]"); Toast.makeText(this,"Mémoire effacée",Toast.LENGTH_SHORT).show();}); box.addView(clearMem);
        Button export=button("Exporter les données (JSON)",panel,white); export.setOnClickListener(v->{try{JSONObject obj=new JSONObject();obj.put("history",new JSONArray(prefs.getString("history","[]")));obj.put("memory",new JSONArray(prefs.getString("memory","[]")));obj.put("personality",prefs.getString("personality",""));Intent i=new Intent(Intent.ACTION_SEND);i.setType("application/json");i.putExtra(Intent.EXTRA_SUBJECT,"JARVIS export");i.putExtra(Intent.EXTRA_TEXT,obj.toString(2));startActivity(Intent.createChooser(i,"Exporter JARVIS"));}catch(Exception e){Toast.makeText(this,"Export impossible",Toast.LENGTH_SHORT).show();}}); box.addView(export);
        Button clearHistory=button("Effacer l'historique",panel,white); clearHistory.setOnClickListener(v->{history=new JSONArray();saveHistory();renderHistory();Toast.makeText(this,"Historique effacé",Toast.LENGTH_SHORT).show();}); box.addView(clearHistory);
        Button save=button("Enregistrer les réglages",mint,bg); save.setOnClickListener(v->{prefs.edit().putString("endpoint",endpoint.getText().toString().trim()).putString("model",model.getText().toString().trim()).putString("key",key.getText().toString().trim()).putString("personality",personality.getText().toString()).apply();status.setText(prefs.getString("endpoint","").isEmpty()?"● Prêt · modèle non configuré":"● Configuration enregistrée");Toast.makeText(this,"Réglages enregistrés",Toast.LENGTH_SHORT).show();}); box.addView(save);
        new android.app.AlertDialog.Builder(this).setView(sv).setPositiveButton("Fermer",(d,w)->{}).show();
    }
    @Override protected void onDestroy(){super.onDestroy();executor.shutdownNow();}
}
