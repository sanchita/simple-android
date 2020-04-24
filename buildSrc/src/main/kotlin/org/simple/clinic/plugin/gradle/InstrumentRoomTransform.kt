package org.simple.clinic.plugin.gradle

import com.android.build.api.transform.DirectoryInput
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.BaseExtension
import javassist.ClassPool
import javassist.CtClass
import javassist.CtConstructor
import javassist.CtMethod
import javassist.expr.ExprEditor
import javassist.expr.MethodCall
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import java.io.File

class InstrumentRoomTransform(
    private val project: Project,
    private val androidPlugin: BaseExtension,
    private var extension: InstrumentRoomExtension
) : Transform() {

  private val logger = project.logger

  override fun getName(): String = "InstrumentRoom"

  override fun getInputTypes(): Set<QualifiedContent.ContentType> {
    return setOf(QualifiedContent.DefaultContentType.CLASSES)
  }

  override fun isIncremental(): Boolean = false

  override fun getScopes(): MutableSet<in QualifiedContent.Scope>? {
    return mutableSetOf(QualifiedContent.Scope.PROJECT)
  }

  override fun transform(transformInvocation: TransformInvocation) {
    val variant = transformInvocation.context.variantName

    val shouldApplyTransform = extension.applyFor.any { variant.endsWith(it, true) }
    logger.logSafely("Transform variant:$variant, apply to: ${extension.applyFor.joinToString()}, Apply transform: $shouldApplyTransform")

    if (shouldApplyTransform) {
      val androidJar = androidPlugin.androidPlatformJarPath()
      val externalDependencyJars = transformInvocation.dependencyJars()
      val externalDependencyDirs = transformInvocation.dependencyDirs()
      val inputClassFiles = transformInvocation
          .inputs
          .flatMap { it.directoryInputs }
          .map { it.file }

      val pool = ClassPool().apply {
        appendSystemPath()
        insertClassPath(androidJar)
        externalDependencyJars.forEach { insertClassPath(it.absolutePath) }
        externalDependencyDirs.forEach { insertClassPath(it.absolutePath) }
        inputClassFiles.forEach { insertClassPath(it.absolutePath) }
      }

      transformInvocation.inputs.forEach { transformInput ->
        transformInput.directoryInputs.forEach { inputDirectory ->
          val roomDaoImplementations = pool.findAllRoomDaoImplementations(inputDirectory)

          logger.logSafely("Room Daos: ${roomDaoImplementations.joinToString { it.name }}")

          roomDaoImplementations
              .forEach { roomCtClass ->

                logger.logSafely("----- BEGIN: ${roomCtClass.name} -----")

                roomCtClass.instrument(object : ExprEditor() {
                  override fun edit(m: MethodCall) {
                    val calledFrom = when(val where = m.where()) {
                      is CtMethod -> where.name
                      is CtConstructor -> "Constructor"
                      else -> "Unknow"
                    }
                    logger.logSafely(" $calledFrom --> ${m.methodName}(${m.signature})")
                  }
                })

                logger.logSafely("----- END: ${roomCtClass.name} -----")
              }
        }
      }
    }
  }
}

private fun ClassPool.findAllRoomDaoImplementations(
    classDirectory: DirectoryInput
): List<CtClass> {
  return classDirectory
      .file
      .walkTopDown()
      .filter(File::isJavaClassFile)
      .map { it.relativeTo(classDirectory.file).toClassname() }
      .filter { it.endsWith("Dao_Impl") }
      .map(this::get)
      .filter(CtClass::isRoomDaoImplementation)
      .toList()
}

private fun TransformInvocation.dependencyJars(): List<File> {
  return referencedInputs
      .flatMap { it.jarInputs }
      .map { it.file }
}

private fun TransformInvocation.dependencyDirs(): List<File> {
  return referencedInputs
      .flatMap { it.directoryInputs }
      .map { it.file }
}

private fun BaseExtension.androidPlatformJarPath(): String {
  val androidSdkDirectory = sdkDirectory.absolutePath

  return "$androidSdkDirectory/platforms/$compileSdkVersion/android.jar"
}

private fun Logger.logSafely(message: String) {
  if (isLifecycleEnabled) {
    lifecycle(message)
  }
}

private val File.isJavaClassFile: Boolean
  get() = isFile && extension == "class"

private fun File.toClassname(): String = path
    .replace("/", ".")
    .replace("\\", ".")
    .replace(".class", "")

private val CtClass.isRoomDaoImplementation: Boolean
  get() {
    // extracting to a local variable since this is actually a method
    // call that does some non-trivial work
    val superclass = superclass
    var hasRoomDaoAnnotation = false

    if (superclass != null) {
      // Check if the super class has the `@Dao` annotation
      hasRoomDaoAnnotation = superclass.isRoomDao
    }

    if(!hasRoomDaoAnnotation) {
      hasRoomDaoAnnotation = interfaces.any(CtClass::isRoomDao)
    }

    return hasRoomDaoAnnotation
  }

private val CtClass.isRoomDao: Boolean
  get() = hasAnnotation("androidx.room.Dao")
