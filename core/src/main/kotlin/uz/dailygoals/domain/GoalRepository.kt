package uz.dailygoals.domain

/** Domain boundary. State observation is wired by the app's data/presentation layer. */
interface GoalRepository {
    suspend fun create(name:String,start:Long,end:Long):String
    suspend fun edit(periodId:String,name:String,start:Long,end:Long)
    suspend fun select(date:Long,periodId:String,value:ResultValue)
    suspend fun archive(periodId:String)
    suspend fun reactivate(goalId:String,name:String,end:Long)
    suspend fun delete(goalId:String)
    suspend fun pending():PendingSummary
    suspend fun statistics(range:DateRange):StatisticsResult
    suspend fun exportData():String
    suspend fun importData(text:String)
    suspend fun deleteAll()
}
class SaveResultUseCase(private val repository:GoalRepository) {
    suspend operator fun invoke(date:Long,periodId:String,value:ResultValue)=repository.select(date,periodId,value)
}
