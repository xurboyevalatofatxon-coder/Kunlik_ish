package uz.dailygoals.domain

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class CoreRulesTest(private val label:String,private val specification:()->Unit) {
    @Test fun fulfillsRule()=specification()
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}")
        fun cases():Collection<Array<Any>> = CoreCases.cases.map { arrayOf<Any>(it.first,it.second) }
    }
}
