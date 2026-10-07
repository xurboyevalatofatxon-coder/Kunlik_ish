package uz.dailygoals.domain

import java.io.File
import kotlin.system.exitProcess
fun main(args:Array<String>) {
    val results=CoreCases.cases.map { (name,test)->
        try { test();println("PASS  $name");Triple(name,true,"") }
        catch(t:Throwable) { println("FAIL  $name: ${t.message}");Triple(name,false,t.stackTraceToString()) }
    }
    val passed=results.count { it.second };println("\nCORE CASES: $passed/${results.size} PASS")
    fun esc(s:String)=s.replace("&","&amp;").replace("<","&lt;").replace("\"","&quot;")
    if(args.isNotEmpty()) File(args[0]).writeText(buildString {
        append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<testsuite name=\"DailyGoalsCore\" tests=\"${results.size}\" failures=\"${results.size-passed}\">\n")
        results.forEach { (name,pass,error)->append("<testcase classname=\"CoreCases\" name=\"${esc(name)}\">");if(!pass)append("<failure>${esc(error)}</failure>");append("</testcase>\n") }
        append("</testsuite>\n")
    })
    if(passed!=results.size) exitProcess(1)
}
