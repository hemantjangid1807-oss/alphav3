package com.alphasim.brain.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.alphasim.brain.data.api.BrainApi
import com.alphasim.brain.data.db.AlphaEntity
import com.alphasim.brain.data.db.AlphaListEntity
import com.alphasim.brain.data.db.overrideSettings
import com.alphasim.brain.data.db.settings
import java.io.File

object CsvExporter {
    private val header = listOf(
        "list", "position", "status", "alpha_id", "sharpe", "fitness", "turnover", "returns", "drawdown", "margin",
        "pnl", "long_count", "short_count", "grade", "failed_checks", "self_corr", "prod_corr", "region", "universe",
        "delay", "decay", "neutralization", "truncation", "expression", "message", "brain_url",
    )

    fun build(list: AlphaListEntity, alphas: List<AlphaEntity>): String {
        val sb = StringBuilder()
        sb.append(header.joinToString(",")).append('\n')
        val base = list.settings
        for (a in alphas) {
            val s = a.overrideSettings ?: base
            val row = listOf(
                list.name, a.position.toString(), a.status, a.alphaId.orEmpty(),
                a.sharpe.str(), a.fitness.str(), a.turnover.str(), a.returns.str(), a.drawdown.str(), a.margin.str(),
                a.pnl.str(), a.longCount?.toString().orEmpty(), a.shortCount?.toString().orEmpty(), a.grade.orEmpty(),
                a.failedChecks?.toString().orEmpty(), a.selfCorr.str(), a.prodCorr.str(),
                s.region, s.universe, s.delay.toString(), s.decay.toString(), s.neutralization, s.truncation.toString(),
                a.expression, a.message.orEmpty(), a.alphaId?.let { BrainApi.alphaWebUrl(it) }.orEmpty(),
            )
            sb.append(row.joinToString(",") { escape(it) }).append('\n')
        }
        return sb.toString()
    }

    fun writeTo(context: Context, uri: Uri, csv: String): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(csv.toByteArray(Charsets.UTF_8)) } != null
    }.getOrDefault(false)

    fun share(context: Context, fileName: String, csv: String) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safe = fileName.replace(Regex("[^A-Za-z0-9._-]+"), "_").ifBlank { "alphas" }
        val file = File(dir, if (safe.endsWith(".csv")) safe else "$safe.csv")
        file.writeText(csv)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share results").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun Double?.str() = this?.toString().orEmpty()

    private fun escape(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
}
