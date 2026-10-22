package com.example.voicecalculator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.*

private val Bg = Color.Black
private val Key = Color(0xFF242424)
private val Accent = Color(0xFFFF8120)
private val Txt = Color(0xFFF4F4F4)
private val Muted = Color(0xFF999999)

class MainActivity : ComponentActivity() {
    private var speechCallback: ((String) -> Unit)? = null
    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) speechCallback?.invoke(spoken)
    }
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startSpeech()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CalculatorApp(onSpeak = { callback ->
            speechCallback = callback
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startSpeech()
            else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }) }
    }
    private fun startSpeech() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar,en")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a calculation / تحدث بالعملية الحسابية")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try { speechLauncher.launch(intent) } catch (_: Exception) { speechCallback?.invoke("Speech recognition unavailable") }
    }
}

@Composable
fun CalculatorApp(onSpeak: ((String) -> Unit) -> Unit) {
    var expr by remember { mutableStateOf("0") }
    var result by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("اضغط الميكروفون وتحدث بالعربية أو الإنجليزية") }
    var history by remember { mutableStateOf(listOf<String>()) }
    var second by remember { mutableStateOf(false) }

    fun append(s: String) { expr = if (expr == "0" || expr == "Error") s else expr + s; result = ""; message = "" }
    fun calculate() {
        try {
            val normalized = normalizeExpression(expr)
            val value = ExpressionParser(normalized).parse()
            result = formatNumber(value)
            history = (listOf("$expr = $result") + history).take(30)
            expr = result
            message = ""
        } catch (_: Exception) { result = "Error"; message = "تحقق من العملية / Check expression" }
    }
    Column(Modifier.fillMaxSize().background(Bg).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("الحاسبة", color = Txt, fontSize = 17.sp, modifier = Modifier.padding(10.dp))
            Text("·", color = Muted, fontSize = 22.sp)
            Text("المحوّل", color = Muted, fontSize = 17.sp, modifier = Modifier.padding(10.dp))
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { message = if (history.isEmpty()) "لا يوجد سجل" else history.take(4).joinToString("\n") }) {
                Icon(Icons.Default.History, null, tint = Muted)
            }
        }
        Spacer(Modifier.weight(1f))
        Text(expr, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), color = Txt, fontSize = 42.sp, textAlign = TextAlign.End, maxLines = 2)
        if (result.isNotBlank() && result != "Error") Text("= $result", modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), color = Muted, fontSize = 20.sp, textAlign = TextAlign.End)
        if (message.isNotBlank()) Text(message, modifier = Modifier.fillMaxWidth().padding(6.dp), color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            CalculatorKey("🎙", Modifier.weight(1f), Accent) { onSpeak { spoken ->
                val parsed = normalizeSpeech(spoken)
                expr = parsed
                message = if (parsed == spoken && !parsed.any { it.isDigit() }) "لم أفهم العملية — Try again" else "تم التعرف على: $spoken"
                result = ""
            } }
            CalculatorKey("deg", Modifier.weight(1f), Muted) { message = "الزوايا بالدرجات" }
            CalculatorKey("(", Modifier.weight(1f), Muted) { append("(") }
            CalculatorKey(")", Modifier.weight(1f), Muted) { append(")") }
            CalculatorKey("AC", Modifier.weight(1f), Accent) { expr = "0"; result = ""; message = "" }
            CalculatorKey("⌫", Modifier.weight(1f), Accent) { expr = if (expr.length <= 1) "0" else expr.dropLast(1) }
        }
        val rows = listOf(
            listOf("2nd", "1/x", "π", "7", "8", "9", "÷"),
            listOf("sin", "tan", "cos", "4", "5", "6", "×"),
            listOf("x!", "√x", "xʸ", "1", "2", "3", "−"),
            listOf("lg", "ln", "e", "00", "0", ".", "+")
        )
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                row.forEach { label ->
                    val isOp = label in listOf("÷","×","−","+")
                    CalculatorKey(label, Modifier.weight(1f).fillMaxHeight(.88f), if (isOp) Accent else if (label in listOf("sin","tan","cos","x!","√x","xʸ","lg","ln","1/x","2nd","π","e")) Muted else Txt) {
                        when (label) {
                            "2nd" -> { second = !second; message = if(second) "2nd functions enabled" else "" }
                            "π" -> append("3.141592653589793")
                            "e" -> append("2.718281828459045")
                            "1/x" -> { expr = "1/($expr)"; calculate() }
                            "√x" -> { expr = "sqrt($expr)"; calculate() }
                            "sin","cos","tan","ln","lg" -> { expr = "$label($expr)"; calculate() }
                            "x!" -> { expr = "fact($expr)"; calculate() }
                            "xʸ" -> append("^")
                            "÷" -> append("/")
                            "×" -> append("*")
                            "−" -> append("-")
                            else -> append(label)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            CalculatorKey("%", Modifier.weight(1f).fillMaxHeight(.88f), Accent) { expr = "($expr)/100"; calculate() }
            CalculatorKey("Voice", Modifier.weight(2f).fillMaxHeight(.88f), Accent) { onSpeak { spoken -> expr = normalizeSpeech(spoken); message = "Recognized: $spoken"; result = "" } }
            CalculatorKey("=", Modifier.weight(2f).fillMaxHeight(.88f), Color(0xFFFF8120)) { calculate() }
        }
    }
}

@Composable
fun CalculatorKey(label: String, modifier: Modifier, foreground: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(22.dp), color = if (label == "=") Accent else Key) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = if (label == "=") Color.White else foreground, fontSize = if (label.length > 3) 16.sp else 22.sp, fontWeight = if (label == "=") FontWeight.Medium else FontWeight.Normal)
        }
    }
}

private fun normalizeSpeech(raw: String): String {
    var s = raw.lowercase(Locale.ROOT)
        .replace("٠","0").replace("١","1").replace("٢","2").replace("٣","3").replace("٤","4")
        .replace("٥","5").replace("٦","6").replace("٧","7").replace("٨","8").replace("٩","9")
        .replace("صفر","0").replace("واحد","1").replace("اثنان","2").replace("اثنين","2").replace("ثلاثة","3")
        .replace("أربعة","4").replace("اربعة","4").replace("خمسة","5").replace("ستة","6").replace("سبعة","7").replace("ثمانية","8").replace("تسعة","9")
        .replace("زائد","+").replace("جمع","+").replace("plus","+").replace("added to","+")
        .replace("ناقص","-").replace("طرح","-").replace("minus","-").replace("subtract","-")
        .replace("ضرب","*").replace("في","*").replace("times","*").replace("multiplied by","*")
        .replace("قسمة على","/").replace("مقسوم على","/").replace("divided by","/").replace("divide by","/")
        .replace("يساوي","").replace("كم يساوي","").replace("what is","").replace("calculate","")
        .replace("ناتج","").replace("ما ناتج","")
    return s.replace(Regex("[^0-9+*/().%\\-^ ]"), "").trim().ifBlank { raw }
}

private fun normalizeExpression(s: String) = s.replace("×","*").replace("÷","/").replace("−","-").replace("π","3.141592653589793")

private fun formatNumber(n: Double): String {
    if (!n.isFinite()) throw ArithmeticException()
    return if (n == n.toLong().toDouble()) n.toLong().toString() else "%.10f".format(Locale.US, n).trimEnd('0').trimEnd('.')
}

private class ExpressionParser(private val input: String) {
    private var pos = 0
    fun parse(): Double { val v = expression(); skip(); if (pos != input.length) error("Unexpected token"); return v }
    private fun skip() { while (pos < input.length && input[pos].isWhitespace()) pos++ }
    private fun eat(c: Char): Boolean { skip(); if (pos < input.length && input[pos] == c) { pos++; return true }; return false }
    private fun expression(): Double { var x = term(); while (true) { x = when { eat('+') -> x + term(); eat('-') -> x - term(); else -> return x } } }
    private fun term(): Double { var x = power(); while (true) { x = when { eat('*') -> x * power(); eat('/') -> x / power(); eat('%') -> x % power(); else -> return x } } }
    private fun power(): Double { var x = unary(); if (eat('^')) x = x.pow(power()); return x }
    private fun unary(): Double { if (eat('+')) return unary(); if (eat('-')) return -unary(); return atom() }
    private fun atom(): Double {
        skip()
        if (eat('(')) { val x = expression(); if (!eat(')')) error("Missing )"); return x }
        if (input.startsWith("sqrt(", pos)) { pos += 5; val x = expression(); if (!eat(')')) error("Missing )"); return sqrt(x) }
        if (input.startsWith("fact(", pos)) { pos += 5; val x = expression(); if (!eat(')')) error("Missing )"); if (x < 0 || x > 170 || x % 1.0 != 0.0) error("Invalid factorial"); return (1..x.toInt()).fold(1.0) { a,b -> a*b } }
        if (input.startsWith("sin(", pos) || input.startsWith("cos(", pos) || input.startsWith("tan(", pos) || input.startsWith("ln(", pos) || input.startsWith("lg(", pos)) {
            val fn = input.substring(pos, input.indexOf('(', pos)); pos = input.indexOf('(', pos)+1
            val x = expression(); if (!eat(')')) error("Missing )")
            return when(fn) { "sin" -> sin(Math.toRadians(x)); "cos" -> cos(Math.toRadians(x)); "tan" -> tan(Math.toRadians(x)); "ln" -> ln(x); else -> log10(x) }
        }
        val start = pos
        while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) pos++
        if (start == pos) error("Number expected")
        return input.substring(start, pos).toDouble()
    }
}
