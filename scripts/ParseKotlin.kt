import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.io.File

fun main(args:Array<String>) {
    val disposable=Disposer.newDisposable()
    try {
        val env=KotlinCoreEnvironment.createForProduction(disposable,CompilerConfiguration(),EnvironmentConfigFiles.JVM_CONFIG_FILES)
        val factory=KtPsiFactory(env.project,false)
        var files=0;var errors=0
        File(args[0]).walkTopDown().filter { it.isFile && (it.extension=="kt" || it.extension=="kts") && it.name!="ParseKotlin.kt" }.forEach { file->
            files++
            val parsed=factory.createFile(file.name,file.readText())
            PsiTreeUtil.collectElementsOfType(parsed,PsiErrorElement::class.java).forEach { error->
                errors++;println("ERROR ${file.path}: ${error.errorDescription} at ${error.textOffset}")
            }
        }
        println("KOTLIN SYNTAX: $files files, $errors syntax errors (not Android type-checking)")
        check(errors==0)
    } finally { Disposer.dispose(disposable) }
}
