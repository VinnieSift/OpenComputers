package li.cil.oc.util

import com.naef.jnlua.NativeSupport.Loader
import com.naef.jnlua.{LuaState, NativeSupport}
import java.io.File
import java.io.FileOutputStream
import java.nio.channels.Channels
import java.util.{Locale, Calendar}
import li.cil.oc.Config
import li.cil.oc.server.component.Computer
import li.cil.oc.util.ExtendedLuaState._
import scala.util.Random

/**
 * Factory singleton used to spawn new LuaState instances.
 *
 * This is realized as a singleton so that we only have to resolve shared
 * library references once during initialization and can then re-use the
 * already loaded ones.
 */
object LuaStateFactory {
  // ----------------------------------------------------------------------- //
  // Initialization
  // ----------------------------------------------------------------------- //

  // Since we use native libraries we have to do some work. This includes
  // figuring out what we're running on, so that we can load the proper shared
  // libraries compiled for that system. It also means we have to unpack the
  // shared libraries somewhere so that we can load them, because we cannot
  // load them directly from a JAR.
  {
    // See http://lopica.sourceforge.net/os.html
    val architecture = System.getProperty("os.arch").toLowerCase match {
      case "i386" | "x86" => "32"
      case "amd64" | "x86_64" => "64"
      case "ppc" | "powerpc" => "ppc"
      case _ => ""
    }
    val extension = System.getProperty("os.name").toLowerCase match {
      case name if name.startsWith("linux") => ".so"
      case name if name.startsWith("windows") => ".dll"
      case name if name.startsWith("mac") => ".dylib"
      case _ => ""
    }
    val libPath = "/assets/" + Config.resourceDomain + "/lib/"

    val tmpPath = {
      val path = System.getProperty("java.io.tmpdir")
      if (path.endsWith("/") || path.endsWith("\\")) path
      else path + "/"
    }

    val library = "native." + architecture + extension
    val libraryUrl = classOf[Computer].getResource(libPath + library)
    if (libraryUrl == null) {
      throw new NotImplementedError("Unsupported platform.")
    }
    // Found file with proper extension. Create a temporary file.
    val file = new File(tmpPath + library)
    // Try to delete an old instance of the library, in case we have an update
    // and deleteOnExit fails (which it regularly does on Windows it seems).
    try {
      file.delete()
    }
    catch {
      case t: Throwable => // Ignore.
    }
    // Copy the file contents to the temporary file.
    try {
      val in = Channels.newChannel(libraryUrl.openStream())
      val out = new FileOutputStream(file).getChannel
      out.transferFrom(in, 0, Long.MaxValue)
      in.close()
      out.close()
      file.deleteOnExit()
    }
    catch {
      // Java (or Windows?) locks the library file when opening it, so any
      // further tries to update it while another instance is still running
      // will fail. We still want to try each time, since the files may have
      // been updated.
      case t: Throwable => // Nothing.
    }

    // Remember the temporary file's location for the loader.
    val libraryPath = file.getAbsolutePath

    // Register a custom library loader with JNLua to actually load the ones we
    // just extracted.
    NativeSupport.getInstance().setLoader(new Loader {
      def load() {
        System.load(libraryPath)
      }
    })
  }

  // ----------------------------------------------------------------------- //
  // Factory
  // ----------------------------------------------------------------------- //

  def createState(): Option[LuaState] = {
    val state = new LuaState(Int.MaxValue)
    try {
      // Load all libraries.
      state.openLib(LuaState.Library.BASE)
      state.openLib(LuaState.Library.BIT32)
      state.openLib(LuaState.Library.COROUTINE)
      state.openLib(LuaState.Library.DEBUG)
      state.openLib(LuaState.Library.ERIS)
      state.openLib(LuaState.Library.MATH)
      state.openLib(LuaState.Library.STRING)
      state.openLib(LuaState.Library.TABLE)
      state.pop(8)

      // Prepare table for os stuff.
      state.newTable()
      state.setGlobal("os")

      // Remove some other functions we don't need and are dangerous.
      state.pushNil()
      state.setGlobal("dofile")
      state.pushNil()
      state.setGlobal("loadfile")
      state.pushNil()
      state.setGlobal("module")
      state.pushNil()
      state.setGlobal("require")

      // Push a couple of functions that override original Lua API functions or
      // that add new functionality to it.
      state.getGlobal("os")

      // Allow getting the real world time via os.realTime() for timeouts.
      state.pushScalaFunction(lua => {
        lua.pushNumber(System.currentTimeMillis() / 1000.0)
        1
      })
      state.setField(-2, "realTime")

      // Date-time formatting using Java's formatting capabilities.
      state.pushScalaFunction(lua => {
        val calendar = Calendar.getInstance(Locale.ENGLISH)
        calendar.setTimeInMillis(lua.checkInteger(1))
        // TODO
        1
      })
      state.setField(-2, "date")

      // Custom os.difftime(). For most Lua implementations this would be the
      // same anyway, but just to be on the safe side.
      state.pushScalaFunction(lua => {
        val t2 = lua.checkNumber(1)
        val t1 = lua.checkNumber(2)
        lua.pushNumber(t2 - t1)
        1
      })
      state.setField(-2, "difftime")

      // Pop the os table.
      state.pop(1)

      state.getGlobal("math")

      // We give each Lua state it's own randomizer, since otherwise they'd
      // use the good old rand() from C. Which can be terrible, and isn't
      // necessarily thread-safe.
      val random = new Random
      state.pushScalaFunction(lua => {
        lua.getTop match {
          case 0 => lua.pushNumber(random.nextDouble())
          case 1 => {
            val u = lua.checkInteger(1)
            lua.checkArg(1, 1 < u, "interval is empty")
            lua.pushInteger(1 + random.nextInt(u))
          }
          case 2 => {
            val l = lua.checkInteger(1)
            val u = lua.checkInteger(2)
            lua.checkArg(1, l < u, "interval is empty")
            lua.pushInteger(l + random.nextInt(u - l))
          }
          case _ => throw new IllegalArgumentException("wrong number of arguments")
        }
        1
      })
      state.setField(-2, "random")

      state.pushScalaFunction(lua => {
        random.setSeed(lua.checkInteger(1))
        0
      })
      state.setField(-2, "randomseed")

      // Pop the math table.
      state.pop(1)

      // Provide some better Unicode support.
      state.getGlobal("string")

      // Rename stuff for binary functionality, to allow byte-wise operations
      // operations on the string.
      state.getField(-1, "sub")
      state.setField(-2, "bsub")

      state.getField(-1, "reverse")
      state.setField(-2, "breverse")

      state.pushScalaFunction(lua => {
        lua.pushString(String.valueOf((1 to lua.getTop).map(lua.checkInteger).map(_.toChar).toArray))
        1
      })
      state.setField(-2, "char")

      // TODO find (probably not necessary?)

      // TODO format (probably not necessary?)

      // TODO gmatch (probably not necessary?)

      // TODO gsub (probably not necessary?)

      state.pushScalaFunction(lua => {
        lua.pushInteger(lua.checkString(1).length)
        1
      })
      state.setField(-2, "len")

      state.pushScalaFunction(lua => {
        lua.pushString(lua.checkString(1).toLowerCase)
        1
      })
      state.setField(-2, "lower")

      // TODO match (probably not necessary?)

      state.pushScalaFunction(lua => {
        lua.pushString(lua.checkString(1).reverse)
        1
      })
      state.setField(-2, "reverse")

      state.pushScalaFunction(lua => {
        val string = lua.checkString(1)
        val start = (lua.checkInteger(2) match {
          case i if i < 0 => string.length + i
          case i => i - 1
        }) max 0
        val end =
          if (lua.getTop > 2) (lua.checkInteger(3) match {
            case i if i < 0 => string.length + i + 1
            case i => i
          }) min string.length
          else string.length
        if (end <= start) lua.pushString("")
        else lua.pushString(string.substring(start, end))
        1
      })
      state.setField(-2, "sub")

      state.pushScalaFunction(lua => {
        lua.pushString(lua.checkString(1).toUpperCase)
        1
      })
      state.setField(-2, "upper")

      // Pop the string table.
      state.pop(1)

      Some(state)
    } catch {
      case ex: Throwable => {
        ex.printStackTrace()
        state.close()
        return None
      }
    }
  }
}