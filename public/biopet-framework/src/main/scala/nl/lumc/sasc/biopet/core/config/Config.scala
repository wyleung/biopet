package nl.lumc.sasc.biopet.core.config

import java.io.File
import nl.lumc.sasc.biopet.core.Logging
import nl.lumc.sasc.biopet.utils.ConfigUtils._

class Config(var map: Map[String, Any]) extends Logging {
  logger.debug("Init phase of config")
  def this() = {
    this(Map())
    loadDefaultConfig()
  }

  def loadConfigEnv(valueName: String) {
    val globalFiles = sys.env.get(valueName).getOrElse("").split(":")
    if (globalFiles.isEmpty) logger.info(valueName + " value not found, no global config is loaded")
    for (globalFile <- globalFiles) {
      var file: File = new File(globalFile)
      if (file.exists()) {
        logger.info("Loading config file: " + file)
        loadConfigFile(file)
      } else logger.warn(valueName + " value found but file does not exist, no global config is loaded")
    }
  }

  def loadDefaultConfig() {
    loadConfigEnv("BIOPET_CONFIG")
  }

  def loadConfigFile(configFile: File) {
    val configMap = fileToConfigMap(configFile)

    if (map.isEmpty) map = configMap
    else map = mergeMaps(configMap, map)
    logger.debug("New config: " + map)
  }

  protected[config] var notFoundCache: List[ConfigValueIndex] = List()
  protected[config] var foundCache: Map[ConfigValueIndex, ConfigValue] = Map()
  protected[config] var defaultCache: Map[ConfigValueIndex, ConfigValue] = Map()

  def contains(s: String): Boolean = map.contains(s)
  def contains(requestedIndex: ConfigValueIndex, freeVar: Boolean): Boolean = contains(requestedIndex.module, requestedIndex.path, requestedIndex.key, freeVar)
  def contains(requestedIndex: ConfigValueIndex): Boolean = contains(requestedIndex.module, requestedIndex.path, requestedIndex.key, true)
  def contains(module: String, path: List[String], key: String, freeVar: Boolean = true): Boolean = {
    val requestedIndex = ConfigValueIndex(module, path, key, freeVar)
    if (notFoundCache.contains(requestedIndex)) return false
    else if (foundCache.contains(requestedIndex)) return true
    else {
      val value = Config.getValueFromMap(map, requestedIndex)
      if (value.isDefined) {
        foundCache += (requestedIndex -> value.get)
        return true
      } else {
        notFoundCache +:= requestedIndex
        return false
      }
    }
  }

  protected[config] def apply(module: String, path: List[String], key: String, default: Any = null, freeVar: Boolean = true): ConfigValue = {
    val requestedIndex = ConfigValueIndex(module, path, key)
    if (contains(requestedIndex, freeVar)) return foundCache(requestedIndex)
    else if (default != null) {
      defaultCache += (requestedIndex -> ConfigValue.apply(requestedIndex, null, default, true))
      return defaultCache(requestedIndex)
    } else {
      logger.error("Value in config could not be found but it seems required, index: " + requestedIndex)
      throw new IllegalStateException("Value in config could not be found but it seems required, index: " + requestedIndex)
    }
  }

  def getReport: String = {
    var output: StringBuilder = new StringBuilder
    output.append("Config report, sorted on module:\n")
    var modules: Map[String, StringBuilder] = Map()
    for ((key, value) <- foundCache) {
      val module = key.module
      if (!modules.contains(module)) modules += (module -> new StringBuilder)
      modules(module).append("Found: " + value.toString + "\n")
    }
    for ((key, value) <- defaultCache) {
      val module = key.module
      if (!modules.contains(module)) modules += (module -> new StringBuilder)
      modules(module).append("Default used: " + value.toString + "\n")
    }
    for (value <- notFoundCache) {
      val module = value.module
      if (!modules.contains(module)) modules += (module -> new StringBuilder)
      if (!defaultCache.contains(value)) modules(module).append("Not Found: " + value.toString + "\n")
    }
    for ((key, value) <- modules) {
      output.append("Config options for module: " + key + "\n")
      output.append(value.toString)
      output.append("\n")
    }
    return output.toString
  }

  override def toString(): String = map.toString
}

object Config extends Logging {
  val global = new Config

  def mergeConfigs(config1: Config, config2: Config): Config = new Config(mergeMaps(config1.map, config2.map))

  private def getMapFromPath(map: Map[String, Any], path: List[String]): Map[String, Any] = {
    var returnMap: Map[String, Any] = map
    for (m <- path) {
      if (!returnMap.contains(m)) return Map()
      else returnMap = any2map(returnMap(m))
    }
    return returnMap
  }

  def getValueFromMap(map: Map[String, Any], index: ConfigValueIndex): Option[ConfigValue] = {
    var submodules = index.path.reverse
    while (!submodules.isEmpty) {
      var submodules2 = submodules
      while (!submodules2.isEmpty) {
        val p = getMapFromPath(map, submodules2 ::: index.module :: Nil)
        if (p.contains(index.key)) {
          return Option(ConfigValue(index, ConfigValueIndex(index.module, submodules2, index.key), p(index.key)))
        }
        if (index.freeVar) {
          val p2 = getMapFromPath(map, submodules2)
          if (p2.contains(index.key)) {
            return Option(ConfigValue(index, ConfigValueIndex(index.module, submodules2, index.key), p2(index.key)))
          }
        }
        submodules2 = submodules2.init
      }
      submodules = submodules.tail
    }
    val p = getMapFromPath(map, index.module :: Nil)
    if (p.contains(index.key)) { // Module is not nested
      return Option(ConfigValue(index, ConfigValueIndex(index.module, Nil, index.key), p(index.key)))
    } else if (map.contains(index.key) && index.freeVar) { // Root value of json
      return Option(ConfigValue(index, ConfigValueIndex("", Nil, index.key), map(index.key)))
    } else { // At this point key is not found on the path
      return None
    }
  }
}