package android.net
import java.io.File
class Uri(private val text:String){
 val scheme get()=java.net.URI(text).scheme
 val path:String? get()=java.net.URI(text).path
 override fun toString()=text
 companion object {fun fromFile(file:File)=Uri(file.toURI().toString());fun parse(s:String)=Uri(s)}
}
