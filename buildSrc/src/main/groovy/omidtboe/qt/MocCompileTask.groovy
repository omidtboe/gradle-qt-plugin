package omidtboe.qt

import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction

class MocCompileTask extends SourceTask {
	@OutputDirectory
	File destinationDir

	String moc

	@TaskAction
	void generateMocFile() {
		source.filter { x -> x.isFile() && x.getName().matches(".*?.h.*?") && x.text.contains('Q_OBJECT') }.each {
			def mocFileName = "${destinationDir}/moc_${it.name}.cpp"
			def process = "${moc} -o ${mocFileName} ${it.path}".execute()
			process.waitFor()
			if (process.exitValue()) {
				println process.err.text
			}
		}
	}
}

