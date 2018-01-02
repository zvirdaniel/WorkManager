package cz.zvird.workmanager.controllers

import cz.zvird.workmanager.data.DataFile
import cz.zvird.workmanager.data.DataHolder
import cz.zvird.workmanager.data.MemoryData
import cz.zvird.workmanager.gui.*
import javafx.application.Platform
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.Button
import javafx.scene.control.MenuItem
import javafx.scene.control.TabPane
import javafx.scene.control.TextField
import javafx.scene.layout.StackPane
import javafx.stage.FileChooser.ExtensionFilter
import javafx.stage.Window
import java.io.File
import java.net.URL
import java.util.*
import kotlin.concurrent.thread

/**
 * Controller for the main UI, not containing TableView
 */
class MainController : Initializable {
	@FXML lateinit var tabPane: TabPane
	@FXML lateinit var openFileMenu: MenuItem
	@FXML lateinit var saveFileMenu: MenuItem
	@FXML lateinit var saveAsFileMenu: MenuItem
	@FXML lateinit var newFileMenu: MenuItem
	@FXML lateinit var aboutMenu: MenuItem
	@FXML lateinit var exportMenu: MenuItem
	@FXML lateinit var deleteButton: Button
	@FXML lateinit var newRowButton: Button
	@FXML lateinit var stackPane: StackPane
	@FXML lateinit var hourlyWageField: TextField
	private lateinit var window: Window

	override fun initialize(location: URL?, resources: ResourceBundle?) {
		// Ads the controller to the DataHolder in order to be accessible everywhere
		DataHolder.mainController = this

		newFileMenu.onAction = EventHandler { newFileUI() }
		openFileMenu.onAction = EventHandler { openFileUI() }
		saveFileMenu.onAction = EventHandler { saveFileUI() }
		saveAsFileMenu.onAction = EventHandler { saveFileAsUI() }
		deleteButton.onAction = EventHandler { deleteRow() }
		newRowButton.onAction = EventHandler { newRow() }
		aboutMenu.onAction = EventHandler { showAboutDialog(window) }
		exportMenu.onAction = EventHandler { exportFileUI() }

		// MaskerPane is used to block the UI if needed
		stackPane.children.add(DataHolder.maskerPane)

		// Requests focus, scrolls to the end of the table, and sets DataHolder.currentTab upon every tab selection
		tabPane.selectionModel.selectedIndexProperty().addListener { _, _, newValue ->
			DataHolder.currentTab = newValue.toInt()
			val tableViewController = DataHolder.getCurrentTableViewController()
			Platform.runLater {
				tableViewController.table.requestFocus()
				if (tableViewController.table.items.isNotEmpty()) {
					tableViewController.table.scrollTo(tableViewController.table.items.count() - 1)
				}
			}
		}

		// Select tab with current month
		val currentMonthIndex = Date().toInstant().atZone(DataHolder.zone).toLocalDate().month.value - 1
		tabPane.selectionModel.select(currentMonthIndex)
		DataHolder.currentTab = currentMonthIndex

		// Assigns the variable after it is loaded properly
		Platform.runLater { window = tabPane.scene.window }

		// Recalculates wages
		recalculateMonthlyWage()
	}

	// TODO: recalculate monthly wage
	fun recalculateMonthlyWage(monthNumber: Int = DataHolder.currentTab) {
		val month = MemoryData.getMonth(monthNumber)

		var hours = 0.0
		for (session in month) {
			hours += session.durationProperty.get().toHours()
		}

	}

	/**
	 * Opens an export dialog (file selector, and month range selector) and calls the backend function
	 */
	private fun exportFileUI() {
		val pair = showExportFileDialog(window)
		val monthRange = pair.first
		val file = pair.second

		if (file != null) {
			val blockedTask = object : BlockedTask({
				TODO("Not finished!")
			}) {
				override fun succeeded() {
					savedAsNotification(file.name)
				}

				override fun failed() {
					cantSaveNotification(file.name)
				}
			}

			thread { blockedTask.run() }
		}
	}

	/**
	 * Creates a new row in currently opened tab by calling it's controller
	 */
	private fun newRow() {
		val currentTab = DataHolder.getCurrentTableViewController()
		currentTab.createNewRow()
	}

	/**
	 * Deletes a row by calling the controller for currently opened tab
	 */
	private fun deleteRow() {
		val currentTab = DataHolder.getCurrentTableViewController()
		val currentRow = currentTab.table.selectionModel.selectedItem
		if (currentRow != null) {
			currentTab.removeRow(currentRow)
		}
	}

	/**
	 * Opens a file selector and calls the backend function
	 */
	private fun newFileUI() {
		val year = showYearSelectorDialog(window)

		if (year == null || year <= 0) {
			errorNotification("Špatně zadaný rok!")
			return
		}

		val file = showSaveFileDialog("Vytvořit nový soubor",
				filters = listOf(ExtensionFilter("JSON", "*.json")),
				extension = ".json",
				initialFileName = year.toString(),
				ownerWindow = window)

		if (file != null) {
			try {
				DataFile.new(file, year)
				DataFile.load(file)
				savedAsNotification(file.name)
			} catch (e: Exception) {
				cantSaveNotification(file.name)
			}
		}
	}

	/**
	 * Saves the file and notifies the user
	 */
	private fun saveFileUI() {
		try {
			DataFile.save()
			savedAsNotification(DataFile.retrieve().name)
		} catch (e: Exception) {
			cantSaveNotification(DataFile.retrieve().name)
		}
	}

	/**
	 * Opens a file selector, and calls the backend function
	 */
	private fun saveFileAsUI() {
		val originalFile = DataFile.retrieve()

		val file = showSaveFileDialog(title = "Uložit soubor jako...",
				filters = listOf(ExtensionFilter("JSON", "*.json")),
				initialDir = File(originalFile.parent),
				initialFileName = originalFile.nameWithoutExtension + " kopie",
				extension = ".json",
				ownerWindow = window
		)


		if (file != null) {
			try {
				DataFile.save(file)
				savedAsNotification(file.name)
			} catch (e: Exception) {
				cantSaveNotification(file.name)
			}
		}
	}

	/**
	 * Opens a file selector in home directory, calls backend for opening the file itself
	 */
	private fun openFileUI() {
		val file = showOpenFileDialog(
				title = "Otevřít soubor",
				filters = listOf(ExtensionFilter("JSON", "*.json")),
				ownerWindow = window
		)

		if (file != null) {
			Platform.runLater {
				BlockedTask {
					try {
						DataFile.load(file, true)
					} catch (e: Exception) {
						informativeNotification("Soubor nelze otevřít, nebo není validní.")
					}
				}.run()
			}
		}
	}
}