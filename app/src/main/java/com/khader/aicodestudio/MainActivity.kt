package com.khader.aicodestudio

import android.app.Activity
import android.content.*
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.view.ViewOutlineProvider
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class MainActivity : Activity() {
    private val OPEN = 1
    private val SAVE = 2
    private val DEV = "khader abo salha"
    private val ORANGE = Color.parseColor("#FFC400")
    private val AMBER = Color.parseColor("#FFD54A")
    private val CARD = Color.parseColor("#141414")
    private val MUTED = Color.parseColor("#A0A0A0")

    private val DEFAULT_MODEL = "gemini-3.8-flash"
    private val FALLBACK = "gemini-flash-latest"

    private val up = " UPGRADE the program, do not only fix it: add the missing Android permissions/features (AndroidManifest), settings, features and properties your specialty needs, so the app becomes more luxurious and more useful to the user. Keep every existing feature working and keep the code compilable."
    private val agents = listOf(
        Triple("🛠️", "خبير الأكواد", "You are an expert software engineer." + up + " Specialty: fix bugs and compile errors, refactor, null-safety, error handling, performance (threads, memory, caching), lifecycle safety, runtime permission requests, state saving, add useful utilities, extra settings and required dependencies."),
        Triple("🎨", "خبير الرسوميات والتصميم", "You are an expert in graphics, UI/UX and visual design." + up + " Specialty: luxurious modern UI (dark leather, glass, gold/yellow accents), animations and transitions, shaders, lighting, shadows, materials, textures, icons, smooth 60fps rendering, adaptive layouts, haptics, polish of every screen."),
        Triple("🎬", "خبير الكاميرا والمؤثرات", "You are an expert in camera work, scene direction, VFX and audio." + up + " Specialty: cinematic camera (follow, orbit, zoom, shake, smooth transitions), scene composition and lighting, particles, post effects, sound effects, music, vibration, photo/screenshot capture and any camera/microphone permissions required."),
        Triple("🧠", "خبير المنطق", "You are an expert in game/app logic and architecture." + up + " Specialty: clean architecture, game loop and state machines, collisions, scoring, levels and difficulty, AI behavior, input handling, saving progress, offline support, achievements, tutorial/onboarding, edge cases."),
        Triple("⚖️", "الحَكَم · المراجعة النهائية", "You are the final judge and senior reviewer." + up + " Specialty: merge all specialists' work, remove conflicts and duplicates, verify imports, permissions, manifest and dependencies are consistent, fix anything that would not compile, and deliver the best, most polished and luxurious final version.")
    )

    private lateinit var key: EditText
    private lateinit var model: EditText
    private lateinit var input: EditText
    private lateinit var output: TextView
    private lateinit var go: Button
    private lateinit var bar: ProgressBar
    private lateinit var testMsg: TextView
    private lateinit var testBtn: Button
    private val chips = ArrayList<TextView>()
    private var result = ""
    private var loaded = false
    private val checks = ArrayList<CheckBox>()
    @Volatile private var thinkCfg = true
    @Volatile private var maxTok = 65536
    private val errMsgs = arrayOfNulls<String>(agents.size)
    private lateinit var connCard: LinearLayout
    private var summary = ""
    private var zipOut = false
    private val files = LinkedHashMap<String, String>()
    private var outFiles = LinkedHashMap<String, String>()
    private lateinit var errView: TextView
    private val exts = setOf("kt","java","xml","gradle","kts","json","txt","md","properties","html","js","css","glsl","vert","frag","yml","yaml","toml","pro","cfg","ts","py","c","cpp","h","cs")
    private val singleMode = " Reply with the COMPLETE updated code ONLY, no markdown fences. At the very top put an Arabic comment: changelog + list of permissions/dependencies to add."
    private val projMode = " The input is a multi-file project; each file starts with a line '=== FILE: path ==='. Reply ONLY with files you modified or added, each preceded by the same marker line followed by the FULL new content of that file. You may add new files and edit AndroidManifest.xml / build.gradle for permissions and dependencies. Omit unchanged files. No markdown fences, no explanations."

    private fun resolveModel(): String =
        model.text.toString().trim().removePrefix("models/").ifBlank { DEFAULT_MODEL }

    private fun clip(v: android.view.View, r: Int) {
        v.outlineProvider = object : ViewOutlineProvider() { override fun getOutline(view: android.view.View, o: Outline) { o.setRoundRect(0, 0, view.width, view.height, dp(r).toFloat()) } }
        v.clipToOutline = true
    }

    private fun plate(t: String) = TextView(this).apply {
        text = t; setTextColor(AMBER); textSize = 13f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
        background = bg(Color.parseColor("#F2080808"), 16, ORANGE); setPadding(dp(16), dp(8), dp(16), dp(8))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun bg(color: Int, r: Int = 20, stroke: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(r).toFloat(); if (stroke != 0) setStroke(dp(1), stroke)
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#070707"), Color.parseColor("#181818"))).apply { cornerRadius = dp(22).toFloat(); setStroke(dp(1), Color.parseColor("#66FFFFFF")) }
        setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(7), 0, dp(7)) }
    }

    private fun label(t: String) = TextView(this).apply { text = t; setTextColor(AMBER); textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, dp(8)) }

    private fun field(h: String, pass: Boolean = false) = EditText(this).apply {
        hint = h; setTextColor(Color.WHITE); setHintTextColor(MUTED); textSize = 13f
        background = bg(Color.parseColor("#090909"), 14, Color.parseColor("#44FFFFFF")); setPadding(dp(12), dp(12), dp(12), dp(12))
        if (pass) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(8)) }
    }

    private fun btn(t: String, primary: Boolean = true, f: () -> Unit) = Button(this).apply {
        text = t; setTextColor(if (primary) Color.BLACK else Color.WHITE); isAllCaps = false; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
        background = if (primary) GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(ORANGE, AMBER)).apply { cornerRadius = dp(16).toFloat() }
        else bg(Color.parseColor("#1C1C1C"), 16, Color.parseColor("#55FFFFFF"))
        setOnClickListener { f() }
        layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
    }

    private fun row(vararg v: android.view.View) = LinearLayout(this).apply { v.forEach { addView(it) } }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = Color.BLACK
        val sp = getSharedPreferences("s", MODE_PRIVATE)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(40), dp(16), dp(24)) }
        val sv = ScrollView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            addView(root)
        }

        root.addView(TextView(this).apply {
            text = "⚙️ الإعدادات"; setTextColor(Color.WHITE); textSize = 13f
            background = bg(Color.parseColor("#1C1C1C"), 16, Color.parseColor("#55FFFFFF")); setPadding(dp(14), dp(8), dp(14), dp(8))
            setOnClickListener { connCard.visibility = if (connCard.visibility == android.view.View.GONE) android.view.View.VISIBLE else android.view.View.GONE }
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.END; setMargins(0, 0, 0, dp(8)) }
        })
        // الترويسة (مكعبات صفراء وسوداء)
        root.addView(FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(185))
            clip(this, 26)
            addView(ImageView(this@MainActivity).apply { setImageResource(R.drawable.cubes_yellow); scaleType = ImageView.ScaleType.CENTER_CROP })
            addView(android.view.View(this@MainActivity).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#80000000"), Color.parseColor("#E6000000"))) })
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(16), dp(10), dp(16), dp(10))
                addView(TextView(this@MainActivity).apply { text = "⚡ AI Code Studio"; setTextColor(ORANGE); textSize = 27f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER })
                addView(TextView(this@MainActivity).apply { text = "فريق خبراء + حَكَم لتطوير تطبيقك وإخراجه بأفخم صورة"; setTextColor(Color.WHITE); textSize = 12.5f; gravity = Gravity.CENTER; setPadding(0, dp(4), 0, dp(12)) })
                addView(plate("👨‍💻 المطور: $DEV"))
            })
        })

        // الإعدادات
        val savedModel = sp.getString("m", DEFAULT_MODEL)?.trim()?.removePrefix("models/")?.ifBlank { DEFAULT_MODEL } ?: DEFAULT_MODEL
        key = field("مفتاح Gemini API (AQ... أو AIza...)", true).apply { setText(sp.getString("k", "")) }
        model = field("النموذج (مثال: $DEFAULT_MODEL)").apply { setText(savedModel) }
        testMsg = TextView(this).apply { text = "لم يتم الاختبار بعد"; setTextColor(MUTED); textSize = 12f; setPadding(dp(4), dp(6), dp(4), dp(2)) }
        testBtn = btn("🔍 فحص المفتاح") { testConnection(sp) }.apply { layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { setMargins(0, dp(2), 0, dp(2)) } }
        connCard = card().apply { addView(label("🔑 إعدادات المفتاح")); addView(key); addView(model); addView(testBtn); addView(testMsg) }
        connCard.visibility = if (key.text.isBlank()) android.view.View.VISIBLE else android.view.View.GONE
        root.addView(connCard)

        // الكود
        input = field("الصق الكود هنا أو ارفع ملفاً…").apply { minLines = 7; maxLines = 12; gravity = Gravity.TOP; typeface = Typeface.MONOSPACE }
        root.addView(card().apply {
            addView(label("📄 الكود")); addView(input)
            addView(row(
                btn("📂 رفع ملف", false) { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }, OPEN) },
                btn("📋 لصق", false) {
                    val c = (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
                    if (c != null && c.itemCount > 0) input.setText(c.getItemAt(0).coerceToText(this@MainActivity))
                }))
        })

        go = btn("🚀 ابدأ المعالجة") { start(sp) }.apply { layoutParams = LinearLayout.LayoutParams(-1, dp(54)).apply { setMargins(0, dp(6), 0, dp(6)) } }
        root.addView(go)
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = agents.size; progressTintList = android.content.res.ColorStateList.valueOf(ORANGE) }
        root.addView(bar)

        // الخبراء
        root.addView(card().apply {
            errView = TextView(this@MainActivity).apply { setTextColor(Color.parseColor("#FF6B5E")); textSize = 12f; setTextIsSelectable(true); visibility = android.view.View.GONE; setPadding(0, 0, 0, dp(8)) }
            addView(label("👥 اختر الخبراء قبل البدء")); addView(errView)
            agents.forEachIndexed { i, a ->
                val chip = TextView(this@MainActivity).apply { text = "في الانتظار"; textSize = 11f; setTextColor(MUTED); background = bg(Color.parseColor("#222222"), 12); setPadding(dp(10), dp(4), dp(10), dp(4))
                    setOnClickListener { errMsgs[i]?.let { m -> toast(m) } } }
                chips.add(chip)
                val cb = CheckBox(this@MainActivity).apply {
                    isChecked = sp.getBoolean("e$i", true); buttonTintList = android.content.res.ColorStateList.valueOf(ORANGE)
                    setOnCheckedChangeListener { _, v -> sp.edit().putBoolean("e$i", v).apply() }
                }
                checks.add(cb)
                addView(LinearLayout(this@MainActivity).apply {
                    gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(4), 0, dp(4))
                    addView(cb)
                    addView(TextView(this@MainActivity).apply { text = "${a.first}  ${a.second}"; setTextColor(Color.WHITE); textSize = 13.5f; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
                    addView(chip)
                })
            }
        })

        // النتيجة
        output = TextView(this).apply { setTextColor(Color.parseColor("#E0E0F0")); setTextIsSelectable(true); typeface = Typeface.MONOSPACE; textSize = 11f; hint = "ستظهر النسخة النهائية هنا"; setHintTextColor(MUTED) }
        root.addView(card().apply {
            addView(label("✅ الكود النهائي")); addView(output)
            addView(row(
                btn("نسخ") { (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("code", result)); toast("تم النسخ") },
                btn("💾 تحميل") {
                    if (result.isBlank()) toast("لا يوجد كود بعد") else startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = if (zipOut) "application/zip" else "text/plain"; putExtra(Intent.EXTRA_TITLE, if (zipOut) "final_project.zip" else "final_code.txt") }, SAVE)
                },
                btn("مشاركة", false) { if (result.isNotBlank()) startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, result) }, "مشاركة")) }))
        })

        root.addView(plate("صُنع بـ ❤️ بواسطة $DEV").apply { layoutParams = LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER; setMargins(0, dp(18), 0, 0) } })
        val frame = FrameLayout(this)
        frame.addView(ImageView(this).apply { setImageResource(R.drawable.bg_leather); scaleType = ImageView.ScaleType.CENTER_CROP })
        frame.addView(android.view.View(this).apply { setBackgroundColor(Color.parseColor("#B3000000")) })
        frame.addView(sv)
        setContentView(frame)
    }

    override fun onActivityResult(req: Int, res: Int, d: Intent?) {
        super.onActivityResult(req, res, d)
        val u: Uri = d?.data ?: return
        if (res != RESULT_OK) return
        try {
            if (req == OPEN) {
                val bytes = contentResolver.openInputStream(u)!!.use { it.readBytes() }
                if (bytes.size > 3 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) loadZip(bytes)
                else {
                    val t = String(bytes)
                    if (t.contains('\u0000') || t.contains('\uFFFD')) { toast("الملف ليس نصياً. ارفعه كملف كود أو zip"); return }
                    loaded = false; input.setText(t)
                }
            }
            if (req == SAVE) {
                contentResolver.openOutputStream(u)!!.use { o ->
                    if (zipOut) ZipOutputStream(o).use { z -> outFiles.forEach { (p, c) -> z.putNextEntry(ZipEntry(p)); z.write(c.toByteArray()); z.closeEntry() } }
                    else o.write(result.toByteArray())
                }
                toast("تم الحفظ")
            }
        } catch (e: Exception) { toast("خطأ: ${e.message}") }
    }

    private fun loadZip(bytes: ByteArray) {
        files.clear(); var total = 0; var skipped = 0
        val skip = Regex("(^|/)(build|\\.git|\\.gradle|\\.idea|node_modules)/")
        ZipInputStream(bytes.inputStream()).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val n = e.name
                if (!e.isDirectory && !skip.containsMatchIn(n) && n.substringAfterLast('.', "").lowercase() in exts) {
                    val b = z.readBytes(); val t = String(b)
                    if (b.size <= 300000 && !t.contains('\u0000') && total + t.length <= 400000) { files[n] = t; total += t.length } else skipped++
                }
                e = z.nextEntry
            }
        }
        if (files.isEmpty()) { toast("لا توجد ملفات كود نصية داخل الـ zip"); return }
        summary = "📦 مشروع zip: ${files.size} ملف (${total / 1000} ألف حرف)" +
            (if (total > 150000) "\n⏱️ المشروع كبير وقد يستغرق وقتاً أطول" else "") +
            (if (skipped > 0) "\n⚠️ تم تجاوز $skipped ملف كبير أو ثنائي" else "") + "\n\n" + files.keys.joinToString("\n")
        loaded = true; input.setText(summary)
    }

    private fun pack(m: Map<String, String>) = m.entries.joinToString("\n") { "=== FILE: ${it.key} ===\n${it.value}" }

    private fun applyReply(reply: String, m: MutableMap<String, String>) {
        val ms = Regex("(?m)^[`*\\s]*=== FILE: (.+?) ===[`*\\s]*$").findAll(reply).toList()
        if (ms.isEmpty()) {
            if (m.size == 1) m[m.keys.first()] = strip(reply) else throw Exception("رد غير مفهوم من الخبير (لا توجد علامات ملفات)")
            return
        }
        ms.forEachIndexed { i, x ->
            val end = if (i + 1 < ms.size) ms[i + 1].range.first else reply.length
            val body = strip(reply.substring(x.range.last + 1, end))
            val name = x.groupValues[1].trim().trim('`', '*', ' ')
            if (ms.size == 1 && m.size == 1 && m.containsKey("code")) m["code"] = body else m[name] = body
        }
    }

    private fun friendly(t: String?): String {
        val s = t ?: "خطأ غير معروف"
        val raw = s.take(200)
        return when {
            s.contains("HTTP 429") -> "تجاوزت حدّ الطلبات/الحصة لدى Gemini. انتظر دقيقة ثم أعد المحاولة، أو بدّل النموذج. [$raw]"
            s.contains("HTTP 404") -> "النموذج غير موجود أو غير متاح، غيّر اسمه من الإعدادات (مثلاً $DEFAULT_MODEL). [$raw]"
            s.contains("HTTP 401") || s.contains("HTTP 403") -> "المفتاح مرفوض أو غير مفعّل. [$raw]"
            s.contains("HTTP 400") && s.contains("API key", true) -> "المفتاح غير صالح. [$raw]"
            s.contains("HTTP 500") || s.contains("HTTP 502") || s.contains("HTTP 503") || s.contains("HTTP 504") -> "خوادم Google مشغولة حالياً، أعد المحاولة بعد قليل. [$raw]"
            s.contains("MAX_TOKENS") -> "الرد كبير جداً وتم قطعه؛ قلّل حجم الكود أو شغّل خبيراً واحداً في كل مرة."
            else -> raw
        }
    }

    private fun progress(i: Int, label: String): (Int) -> Unit {
        var last = 0
        return { n -> if (n < last || n - last > 800) { last = n; chip(i, "$label$n", AMBER) } }
    }

    private fun cleanKey() = key.text.toString().filter { !it.isWhitespace() && it != '"' && it != '\'' }

    private fun testConnection(sp: SharedPreferences) {
        val k = cleanKey()
        val m = resolveModel()
        if (k.length < 20) { testMsg.text = "❌ أدخل المفتاح أولاً"; testMsg.setTextColor(Color.parseColor("#F44336")); return }
        testBtn.isEnabled = false; testMsg.text = "⏳ جارٍ الفحص…"; testMsg.setTextColor(AMBER)
        Thread {
            var ok = false
            val msg = try {
                val c = URL("https://generativelanguage.googleapis.com/v1beta/models/$m").openConnection() as HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 8000
                c.setRequestProperty("x-goog-api-key", k)
                val code = c.responseCode
                val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
                val em = try { JSONObject(txt).getJSONObject("error").getString("message") } catch (e: Exception) { "" }
                if (code in 200..299 || code == 404 || code == 429) sp.edit().putString("k", k).putString("m", m).apply()
                when {
                    code in 200..299 -> { ok = true; "✅ المفتاح صالح ومفعّل (النموذج: $m)" }
                    code == 404 -> "⚠️ المفتاح مقبول لكن النموذج «$m» غير موجود، غيّر اسمه"
                    code == 429 -> "⚠️ المفتاح صحيح لكن تجاوزت الحصة"
                    code == 400 && em.contains("key", true) -> "❌ المفتاح غير صالح"
                    code == 401 || code == 403 -> "❌ المفتاح مرفوض أو غير مفعّل: ${em.take(120)}"
                    else -> "❌ HTTP $code${em.take(120)}"
                }
            } catch (e: java.net.SocketTimeoutException) { "❌ انتهت المهلة، تحقق من الإنترنت أو الـ VPN"
            } catch (e: java.net.UnknownHostException) { "❌ لا يوجد اتصال بالإنترنت"
            } catch (e: Exception) { "❌ ${e.message}" }
            runOnUiThread {
                testMsg.text = msg
                testMsg.setTextColor(if (ok || msg.startsWith("⚠️")) Color.parseColor("#4CAF50") else Color.parseColor("#F44336"))
                testBtn.isEnabled = true
                if (ok) testMsg.postDelayed({ connCard.visibility = android.view.View.GONE }, 1200)
            }
        }.start()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun chip(i: Int, t: String, c: Int) = runOnUiThread { chips[i].text = t; chips[i].setTextColor(c) }

    private val judgeMode = " Input = ORIGINAL followed by PROPOSALS from specialists. Merge the best of ALL proposals into one final version, resolving conflicts and duplicates."

    private fun start(sp: SharedPreferences) {
        val k = cleanKey()
        if (k.length < 20) { toast("أدخل مفتاح Gemini من الإعدادات ⚙️"); connCard.visibility = android.view.View.VISIBLE; return }
        val specs = agents.dropLast(1); val ji = specs.size
        val sel = specs.indices.filter { checks[it].isChecked }
        val useJudge = checks[ji].isChecked
        if (sel.isEmpty()) { toast("اختر خبيراً واحداً على الأقل"); return }
        val orig = LinkedHashMap<String, String>()
        val proj = loaded && input.text.toString() == summary
        if (proj) orig.putAll(files) else {
            val t = input.text.toString()
            if (t.isBlank()) { toast("أدخل الكود أولاً"); return }
            orig["code"] = t
        }
        val m = resolveModel()
        sp.edit().putString("k", k).putString("m", m).apply()
        zipOut = proj
        val judging = useJudge && sel.size > 1
        go.isEnabled = false; output.text = ""; result = ""; errView.visibility = android.view.View.GONE
        bar.progress = 0; bar.max = sel.size + (if (judging) 1 else 0)
        chips.forEachIndexed { i, _ -> chip(i, if (checks[i].isChecked) "في الانتظار" else "غير مفعّل", MUTED) }
        val green = Color.parseColor("#4CAF50"); val red = Color.parseColor("#F44336")
        Thread {
            val payload = if (proj) pack(orig) else orig["code"]!!
            val props = arrayOfNulls>(specs.size)
            val errs = errMsgs; errs.fill(null)
            val done = java.util.concurrent.atomic.AtomicInteger(0)
            val gate = java.util.concurrent.Semaphore(2)
            sel.map { i ->
                Thread {
                    chip(i, "في الطابور…", MUTED)
                    gate.acquire()
                    try {
                        chip(i, "جارٍ العمل…", AMBER)
                        val w = LinkedHashMap(orig)
                        applyReply(callRetry(k, m, specs[i].third + (if (proj) projMode else singleMode), payload, progress(i, "جارٍ العمل…")), w)
                        props[i] = w; chip(i, "تم ✓", green)
                    } catch (e: Exception) { errs[i] = friendly(e.message); chip(i, "فشل ✗ (اضغط)", red) }
                    finally { gate.release() }
                    runOnUiThread { bar.progress = done.incrementAndGet() }
                }.also { it.start() }
            }.forEach { it.join() }
            val ok = sel.filter { props[it] != null }
            val allErr = sel.filter { errs[it] != null }.joinToString("\n") { "• ${specs[it].second}:${errs[it]}" }
            try {
                if (ok.isEmpty()) throw Exception(allErr.ifBlank { "فشل جميع الخبراء" })
                val fin = LinkedHashMap(orig)
                var merged = false
                if (judging && ok.size > 1) {
                    val sb = StringBuilder("=== ORIGINAL ===\n$payload")
                    ok.forEach { i ->
                        val w = props[i]!!
                        sb.append("\n\n##### PROPOSAL: ${specs[i].second} #####\n").append(if (proj) pack(w.filter { orig[it.key] != it.value }) else w["code"])
                    }
                    chip(ji, "جارٍ المراجعة…", AMBER)
                    try {
                        applyReply(callRetry(k, m, agents[ji].third + judgeMode + (if (proj) projMode else singleMode), sb.toString(), progress(ji, "جارٍ المراجعة…")), fin)
                        chip(ji, "تم ✓", green); merged = true
                    } catch (e: Exception) { errMsgs[ji] = friendly(e.message); chip(ji, "دمج تلقائي (اضغط)", AMBER) }
                    runOnUiThread { bar.progress = bar.max }
                } else chip(ji, if (useJudge) "تخطّي (خبير واحد)" else "غير مفعّل", MUTED)
                if (!merged) ok.forEach { i -> props[i]!!.forEach { (f, v) -> if (orig[f] != v) fin[f] = v } }
                outFiles = fin
                result = if (proj) pack(fin) else fin["code"]!!
                val shown = if (result.length > 15000) result.take(15000) + "\n\n…(مقتطع للعرض، استخدم زر التحميل للنسخة الكاملة)" else result
                val failed = sel.filter { props[it] == null }.joinToString("، ") { specs[it].second }
                runOnUiThread {
                    output.text = shown; toast("اكتملت المعالجة ✅")
                    if (failed.isNotEmpty()) { errView.text = "⚠️ تعذّر على بعض الخبراء:\n$allErr"; errView.visibility = android.view.View.VISIBLE }
                }
            } catch (e: Exception) {
                chip(ji, "فشل ✗", red)
                runOnUiThread { errView.text = "⚠️ فشل: ${e.message}"; errView.visibility = android.view.View.VISIBLE }
            }
            runOnUiThread { go.isEnabled = true }
        }.start()
    }

    private fun callRetry(k: String, m: String, sys: String, user: String, prog: ((Int) -> Unit)? = null): String {
        var n = 0
        var mm = m
        var fellBack = false
        while (true) {
            try { return call(k, mm, sys, user, prog) }
            catch (e: java.net.UnknownHostException) { throw Exception("لا يوجد اتصال بالإنترنت") }
            catch (e: Exception) {
                val t = e.message ?: ""
                if (t.contains("HTTP 400") && !t.contains("API key", true)) {
                    if (thinkCfg) { thinkCfg = false; continue }
                    if (maxTok > 32768) { maxTok = 32768; continue }
                    if (maxTok > 8192) { maxTok = 8192; continue }
                }
                val is404 = t.contains("HTTP 404")
                val quotaOut = t.contains("PerDay", true) || t.contains("limit: 0")
                val busy = t.contains("HTTP 429") || t.contains("HTTP 500") || t.contains("HTTP 502") || t.contains("HTTP 503") || t.contains("HTTP 504")
                n++
                if (!fellBack && mm != FALLBACK && (is404 || (busy && (n >= 2 || quotaOut)))) {
                    mm = FALLBACK; fellBack = true; n = 0; continue
                }
                val timeout = e is java.net.SocketTimeoutException
                val retry = !quotaOut && (busy || t.contains("رد فارغ") || e is java.io.IOException)
                if (retry && n < (if (timeout) 3 else 5)) {
                    val hint = Regex("retry in ([0-9.]+)s", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1)?.toDoubleOrNull()?.let { (it * 1000).toLong() + 1000L }
                    Thread.sleep(minOf(hint ?: (4000L * n), 60000L))
                } else if (timeout) throw Exception("انتهت المهلة: تعذّر الاتصال بخوادم Google. تحقق من الإنترنت أو أوقف/بدّل الـ VPN ثم اضغط «فحص المفتاح»")
                else throw e
            }
        }
    }

    private fun strip(s: String): String {
        var t = s.trim()
        if (t.startsWith("```")) t = t.substringAfter("\n", "")
        if (t.endsWith("```")) t = t.removeSuffix("```")
        return t.trim()
    }

    // بث مباشر (SSE): يدعم المفاتيح الحديثة AQ. والقديمة AIza عبر ترويسة x-goog-api-key
    private fun call(k: String, m: String, sys: String, user: String, prog: ((Int) -> Unit)? = null): String {
        val gen = JSONObject().put("maxOutputTokens", maxTok)
        if (thinkCfg) gen.put("thinkingConfig", JSONObject().put("thinkingBudget", 1024))
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", user)))))
            .put("generationConfig", gen)
        val c = URL("[https://generativelanguage.googleapis.com/v1beta/models/$m:streamGenerateContent?alt=sse](https://generativelanguage.googleapis.com/v1beta/models/$m:streamGenerateContent?alt=sse)").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 20000; c.readTimeout = 180000
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("x-goog-api-key", k)
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        if (code !in 200..299) {
            val txt = c.errorStream?.bufferedReader()?.readText() ?: ""
            val msg = try { JSONObject(txt).getJSONObject("error").getString("message") } catch (e: Exception) { "" }
            throw Exception("HTTP $code [$m] ${msg.take(300)}")
        }
        val sb = StringBuilder()
        var finish = ""; var block = ""
        c.inputStream.bufferedReader().use { r ->
            while (true) {
                val line = r.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val j = line.removePrefix("data:").trim()
                if (j.isEmpty() || j == "[DONE]") continue
                val o = try { JSONObject(j) } catch (e: Exception) { continue }
                o.optJSONObject("error")?.let { er -> throw Exception("HTTP ${er.optInt("code", 500)} [$m] ${er.optString("message").take(300)}") }
                o.optJSONObject("promptFeedback")?.optString("blockReason")?.takeIf { it.isNotEmpty() }?.let { block = it }
                val cand = o.optJSONArray("candidates")?.optJSONObject(0) ?: continue
                cand.optString("finishReason").takeIf { it.isNotEmpty() && it != "null" }?.let { finish = it }
                val parts = cand.optJSONObject("content")?.optJSONArray("parts") ?: continue
                for (i in 0 until parts.length()) { val p = parts.getJSONObject(i); if (!p.optBoolean("thought")) sb.append(p.optString("text")) }
                prog?.invoke(sb.length)
            }
        }
        if (finish == "MAX_TOKENS") throw Exception("MAX_TOKENS: الرد أطول من الحد المسموح فتم قطعه")
        if (sb.isEmpty()) throw Exception(if (block.isNotEmpty()) "تم حجب الطلب من Gemini ($block)" else "رد فارغ من النموذج ($finish)")
        return sb.toString()
    }
}
