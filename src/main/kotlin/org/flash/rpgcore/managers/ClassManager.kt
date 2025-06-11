package org.flash.rpgcore.managers

import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.flash.rpgcore.RPGcore
import org.flash.rpgcore.classes.RPGClass
import java.io.File

object ClassManager {

    private val plugin = RPGcore.instance
    private val logger = plugin.logger
    private val classesDirectory = File(plugin.dataFolder, "classes")

    private val loadedClasses: MutableMap<String, RPGClass> = mutableMapOf()

    fun loadClasses() {
        loadedClasses.clear()
        logger.info("[ClassManager] 클래스 설정 파일 로드를 시작합니다...")

        if (!classesDirectory.exists()) {
            classesDirectory.mkdirs()
            logger.info("[ClassManager] 'classes' 데이터 폴더를 생성했습니다.")
        }

        // 플러그인에 내장된 기본 클래스 파일 목록
        val defaultClassFiles = listOf(
            "warrior/frenzy_dps.yml",
            "warrior/spike_tank.yml",
            "assassin/gale_striker.yml",
            "ranged_dealer/marksman.yml",
            "mage/elementalist.yml"
        )

        // 기본 클래스 파일이 데이터 폴더에 없으면 resources에서 복사
        defaultClassFiles.forEach { resourcePath ->
            val classFile = File(classesDirectory, resourcePath)
            if (!classFile.exists()) {
                // 부모 디렉토리(예: warrior)가 없을 경우 생성
                classFile.parentFile.mkdirs()
                plugin.saveResource("classes/$resourcePath", false)
                logger.info("[ClassManager] 기본 클래스 파일 'classes/$resourcePath'를 데이터 폴더에 복사했습니다.")
            }
        }

        loadClassesFromDirectory(classesDirectory)
        logger.info("[ClassManager] 총 ${loadedClasses.size}개의 클래스를 로드했습니다.")
    }

    private fun loadClassesFromDirectory(directory: File) {
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                loadClassesFromDirectory(file)
            } else if (file.isFile && file.extension.equals("yml", ignoreCase = true)) {
                loadClassFromFile(file)
            }
        }
    }

    private fun loadClassFromFile(file: File) {
        val config = YamlConfiguration.loadConfiguration(file)
        val classId = file.nameWithoutExtension

        try {
            val rawDisplayName = config.getString("display_name", "&f${classId}") ?: "&f${classId}"
            val displayName = ChatColor.translateAlternateColorCodes('&', rawDisplayName)

            val archetypeInternalId = config.getString("archetype_internal_id", "UNKNOWN_ARCHETYPE")!!
            val rawArchetypeDisplayName = config.getString("archetype_display_name", "&7알 수 없는 계열")!!
            val archetypeDisplayName = ChatColor.translateAlternateColorCodes('&', rawArchetypeDisplayName)

            val rawDescription = config.getStringList("description")
            val description = rawDescription.map { ChatColor.translateAlternateColorCodes('&', it) }

            val rawUniqueMechanicSummary = config.getString("unique_mechanic_summary", "")!!
            val uniqueMechanicSummary = ChatColor.translateAlternateColorCodes('&', rawUniqueMechanicSummary)

            val iconMaterialName = config.getString("icon_material", "STONE")!!.uppercase()
            val iconMaterial = Material.matchMaterial(iconMaterialName) ?: Material.STONE

            val customModelData = if (config.contains("custom_model_data")) config.getInt("custom_model_data") else null
            val allowedMainHandMaterials = config.getStringList("allowed_main_hand_materials")

            val starterSkillsMap = mutableMapOf<String, List<String>>()
            config.getConfigurationSection("starter_skills")?.getKeys(false)?.forEach { skillTypeKey ->
                starterSkillsMap[skillTypeKey.uppercase()] = config.getStringList("starter_skills.$skillTypeKey")
            }

            val innatePassiveSkillIds = config.getStringList("innate_passive_skill_ids")

            val rpgClass = RPGClass(
                internalId = classId,
                displayName = displayName,
                archetypeInternalId = archetypeInternalId,
                archetypeDisplayName = archetypeDisplayName,
                description = description,
                uniqueMechanicSummary = uniqueMechanicSummary,
                iconMaterial = iconMaterial,
                customModelData = customModelData,
                allowedMainHandMaterials = allowedMainHandMaterials,
                starterSkills = starterSkillsMap,
                innatePassiveSkillIds = innatePassiveSkillIds
            )

            if (loadedClasses.containsKey(classId)) {
                logger.warning("[ClassManager] 중복된 클래스 ID '$classId'를 파일 '${file.path}'에서 발견했습니다. 이전에 로드된 클래스를 덮어씁니다.")
            }
            loadedClasses[classId] = rpgClass

        } catch (e: Exception) {
            logger.severe("[ClassManager] 클래스 파일 '${file.path}' 로드 중 오류 발생: ${e.message}")
            e.printStackTrace()
        }
    }

    fun reloadClasses() {
        logger.info("[ClassManager] 모든 클래스 설정을 리로드합니다...")
        loadClasses()
    }

    fun getClass(internalId: String): RPGClass? {
        return loadedClasses[internalId]
    }

    fun getAllClasses(): List<RPGClass> {
        return loadedClasses.values.toList()
    }

    fun getClassesByArchetype(archetypeInternalId: String): List<RPGClass> {
        return loadedClasses.values.filter { it.archetypeInternalId.equals(archetypeInternalId, ignoreCase = true) }
    }
}