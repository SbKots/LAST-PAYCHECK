package lastpay.api

import lastpay.domain.*
import kotlinx.serialization.json.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.http.*
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream

fun ApplicationCall.query(allowed: Set<String>, required: Set<String> = emptySet()): Map<String,String> {
    val q=request.queryParameters
    if(q.names().any { it !in allowed || q.getAll(it)?.size!=1 }) fail(FailureKind.BADREQUEST,"Неизвестный или повторный параметр")
    if(required.any { q[it]==null }) fail(FailureKind.BADREQUEST,"Отсутствует обязательный параметр")
    return q.names().associateWith { q[it]!! }
}
fun Map<String,String>.integer(name: String, default: Int? = null, min: Int, max: Int): Int {
    val raw=this[name] ?: return default ?: fail(FailureKind.BADREQUEST,"Отсутствует $name")
    if(!Regex("^-?[0-9]+$").matches(raw)) fail(FailureKind.BADREQUEST,"Ожидается целый параметр",name)
    val value=raw.toLongOrNull()
    if(value==null || value !in min.toLong()..max.toLong()) fail(FailureKind.VALIDATIONERROR,"Недопустимый диапазон",name)
    return value.toInt()
}
suspend fun ApplicationCall.jsonBody(): JsonObject {
    if(request.contentType().withoutParameters().toString()!="application/json") fail(FailureKind.BADREQUEST,"Требуется application/json")
    val input=receiveChannel();val bytes=ByteArrayOutputStream();val buffer=ByteArray(8192)
    while(true) {
        val size=input.readAvailable(buffer,0,buffer.size)
        if(size<0) break
        if(size==0) continue
        if(bytes.size()+size>1048576) fail(FailureKind.BADREQUEST,"Превышен размер JSON")
        bytes.write(buffer,0,size)
    }
    return try { json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject }
    catch(_:Exception) { fail(FailureKind.BADREQUEST,"Некорректный JSON объект") }
}
fun JsonObject.shape(fields: Set<String>) {
    if(keys!=fields) fail(FailureKind.VALIDATIONERROR,"Неверный набор полей JSON")
}
fun JsonObject.text(name: String, max: Int, nullable: Boolean = false, empty: Boolean = false): String? {
    val element=this[name] ?: fail(FailureKind.VALIDATIONERROR,"Отсутствует поле",name)
    if(element===JsonNull && nullable) return null
    val value=element as? JsonPrimitive ?: fail(FailureKind.BADREQUEST,"Ожидается строка",name)
    if(!value.isString) fail(FailureKind.BADREQUEST,"Ожидается строка",name)
    val content=value.content
    if ('\u0000' in content) fail(FailureKind.VALIDATIONERROR, "Нулевой символ недопустим", name)
    if(content.codePointCount(0,content.length)>max || (!empty && content.isBlank())) fail(FailureKind.VALIDATIONERROR,"Недопустимая длина строки",name)
    return content
}
fun skillInput(body: JsonObject, pathId: String?): SkillInput {
    body.shape(setOf("code","title","description","source","externalId")+(if(pathId==null)setOf("id") else emptySet()))
    val id=pathId ?: body.text("id",64)!!
    val code=body.text("code",64)!!
    if(!Regex("^[a-z0-9][a-z0-9._-]{0,63}$").matches(id) || !Regex("^[a-z0-9][a-z0-9._-]{0,63}$").matches(code)) fail(FailureKind.VALIDATIONERROR,"Некорректный id или code")
    val source=body.text("source",24)!!;val external=body.text("externalId",64,nullable=true,empty=true)
    if(source !in setOf("ONET","DEMO") || (source=="ONET" && external.isNullOrBlank())) fail(FailureKind.VALIDATIONERROR,"Для ONET нужен непустой externalId","externalId")
    return SkillInput(id,code,body.text("title",160)!!,body.text("description",4000,empty=true)!!,source,external)
}
