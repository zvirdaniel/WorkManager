package cz.zvird.workmanager.controllers

import cz.zvird.workmanager.controllers.TableViewController.EditingStateHolder.EditingState.*
import cz.zvird.workmanager.data.DataHolder
import cz.zvird.workmanager.data.MemoryManager
import cz.zvird.workmanager.gui.LocalDateCell
import cz.zvird.workmanager.gui.errorNotification
import cz.zvird.workmanager.models.WorkSession
import javafx.application.Platform
import javafx.beans.value.ChangeListener
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.Label
import javafx.scene.control.TableColumn
import javafx.scene.control.TableRow
import javafx.scene.control.TableView
import javafx.scene.control.cell.TextFieldTableCell
import javafx.scene.input.KeyCode.*
import javafx.scene.input.KeyEvent
import javafx.util.Callback
import javafx.util.StringConverter
import java.net.URL
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*
import kotlin.concurrent.thread

class TableViewController : Initializable {
	@FXML
	lateinit var table: TableView<WorkSession>
	@FXML
	lateinit var date: TableColumn<WorkSession, LocalDate>
	@FXML
	lateinit var time: TableColumn<WorkSession, LocalTime>
	@FXML
	lateinit var duration: TableColumn<WorkSession, Duration>
	@FXML
	lateinit var description: TableColumn<WorkSession, String>

	// The following variables are used in row editor
	private var isEditing = false
	private var editingRow = 0

	override fun initialize(location: URL?, resources: ResourceBundle?) {
		table.placeholder = Label("Žádná data k zobrazení. Lze přidat tlačítkem dole, nebo Ctrl + N.")
		keyHandlers()
		blankRowCallback()
		cellValueFactories()
		cellFactories()
		commitHandlers()
		DataHolder.addTableViewController(this)
	}

	/**
	 * Ctrl+N, Enter and Delete key handlers, consumes the Enter key if row editing is in progress
	 */
	private fun keyHandlers() {
		table.addEventFilter(KeyEvent.KEY_PRESSED, {
			when {
				it.code == DELETE && table.selectionModel.selectedItem != null ->
					removeRow(table.selectionModel.selectedItem)

				it.isControlDown && !it.isShiftDown && it.code == N ->
					createNewRow()

				!it.isControlDown && !it.isShiftDown && it.code == ENTER -> {
					editCurrentRow()

					if (editingRow != table.selectionModel.selectedIndex) {
						it.consume()
					}
				}
			}
		})
	}

	/**
	 * Responsible for saving the data when cell factories detect a change
	 */
	private fun commitHandlers() {
		date.onEditCommit = EventHandler { it.rowValue.beginDateProperty.value = it.newValue }
		description.onEditCommit = EventHandler { it.rowValue.descriptionProperty.value = it.newValue }
		duration.onEditCommit = EventHandler { it.rowValue.durationProperty.value = it.newValue }
		time.onEditCommit = EventHandler { it.rowValue.beginTimeProperty.value = it.newValue }
	}

	/**
	 * Responsible for rendering the data contained within each cell for a single column
	 */
	private fun cellFactories() {
		date.cellFactory = Callback { LocalDateCell() }

		time.cellFactory = TextFieldTableCell.forTableColumn(object : StringConverter<LocalTime>() {
			override fun toString(time: LocalTime): String {
				return time.format(DateTimeFormatter.ofPattern("HH:mm"))
			}

			override fun fromString(string: String): LocalTime {
				try {
					if (string.contains(':')) {
						return LocalTime.parse(string, DateTimeFormatter.ofPattern("HH:mm"))
					}

					if (string.length == 4) {
						return LocalTime.parse(string, DateTimeFormatter.ofPattern("HHmm"))
					}

					return LocalTime.parse(string, DateTimeFormatter.ofPattern("HH"))
				} catch (e: DateTimeParseException) {
					errorNotification("$string není validní čas!")
				}

				return LocalTime.now(DataHolder.zone)
			}
		})

		duration.cellFactory = TextFieldTableCell.forTableColumn(object : StringConverter<Duration>() {
			override fun toString(duration: Duration): String {
				return duration.toString()
						.substring(2)
						.replace("(\\d[HMS])(?!$)".toRegex(), "$1 ")
						.toLowerCase()
			}

			override fun fromString(string: String): Duration {
				try {
					val minutes = string.toLong()
					return Duration.ofMinutes(minutes)
				} catch (e: NumberFormatException) {
					errorNotification("$string není validní počet minut! Pouze celé čísla.")
				}

				return Duration.ofMinutes(30)
			}
		})

		description.cellFactory = TextFieldTableCell.forTableColumn()
	}

	/**
	 * Responsible for populating the data for all cells within a single column
	 */
	private fun cellValueFactories() {
		date.cellValueFactory = Callback { it.value.beginDateProperty }
		time.cellValueFactory = Callback { it.value.beginTimeProperty }
		duration.cellValueFactory = Callback { it.value.durationProperty }
		description.cellValueFactory = Callback { it.value.descriptionProperty }
	}

	/**
	 * Double-click on blank space creates a new row
	 */
	private fun blankRowCallback() {
		table.rowFactory = Callback {
			val row = TableRow<WorkSession>()

			row.onMouseClicked = EventHandler {
				if (it.clickCount >= 2 && row.item !is WorkSession) {
					createNewRow()
				}
			}

			row
		}
	}

	/**
	 * Removes a row if table is not empty
	 */
	fun removeRow(row: WorkSession) {
		if (table.items.isNotEmpty()) {
			table.items.remove(row)
		}
	}

	/**
	 * Creates new row with some data, scrolls to the end of the table and selects it
	 */
	fun createNewRow() {
		val session: WorkSession
		val lastSession = table.items.lastOrNull()

		if (lastSession != null) {
			val lastDatePlusOneDay = lastSession.beginDateProperty.get().plusDays(1)
			session = if (lastDatePlusOneDay.monthValue == DataHolder.currentMonth) {
				val lastDateTime = LocalDateTime.of(lastDatePlusOneDay, LocalTime.of(12, 0))
				WorkSession(lastDateTime, 180, lastSession.descriptionProperty.value)
			} else {
				val lastDate = lastSession.beginDateProperty.get()
				val lastDateTime = LocalDateTime.of(lastDate, LocalTime.of(12, 0))
				WorkSession(lastDateTime, 180, lastSession.descriptionProperty.value)
			}
		} else {
			session = if (DataHolder.currentMonth == LocalDate.now(DataHolder.zone).monthValue) {
				WorkSession(addMinutes = 180, description = "Doplnit!")
			} else {
				val localDate = LocalDate.of(MemoryManager.currentYear, DataHolder.currentMonth, 1)
				val dateTime = LocalDateTime.of(localDate, LocalTime.of(12, 0))
				WorkSession(dateTime, 180, "Doplnit!")
			}
		}

		table.items.add(session)
		Platform.runLater {
			table.scrollTo(table.items.last())
			table.requestFocus()
			table.selectionModel.selectLast()
		}
	}


	/**
	 * Listener informs the row editor about changes in the current column by editing the state
	 * @param state to modify when the user makes a change to the column
	 * @return ChangeListener that can be added or removed from any given observable value
	 */
	private fun <T> generateListener(state: EditingStateHolder): ChangeListener<T> = ChangeListener { _, _, _ ->
		state.value = FINISHED
	}

	/**
	 * State can not be stored simply in the enum variable, because just it's value is being passed to the function, not the variable reference,
	 * therefore those functions can not edit the state. Example: fun (s: State) { s = INIT } will not work, because the reference to the
	 * variable "s" is not passed, only it's value is. So the function actually attempts something like "INIT = INIT" which does not work.
	 * The solution is to hold the variable in a class, and pass the class instance to the functions instead.
	 */
	private class EditingStateHolder(initial: EditingState) {
		enum class EditingState { EDITING, CANCELLED, FINISHED }

		var value = initial
	}

	/**
	 * Edits the currently selected row, cell after cell
	 */
	private fun editCurrentRow() {
		if (!isEditing) {
			editingRow = table.selectionModel.selectedIndex
			isEditing = true
			println("INFO: Editing row $editingRow")

			thread {
				while (isEditing) {
					try {
						editCell(date)
						editCell(time)
						editCell(duration)
						editCell(description)

						Thread.sleep(100)
						Platform.runLater {
							table.requestFocus()
							table.selectionModel.select(editingRow)
							table.focusModel.focus(editingRow)

						}
					} catch (e: IllegalStateException) {
						println("WARNING: ${e.message}")
						isEditing = false
					}

					isEditing = false
				}
			}
		}
	}

	/**
	 * @param column specified to edit on a given row
	 * @throws IllegalStateException if editing goes on for over 5 seconds
	 */
	private fun <T> editCell(column: TableColumn<WorkSession, T>) {
		var time = 0
		val state = EditingStateHolder(EDITING)
		val listener = generateListener<T>(state)
		column.getCellObservableValue(editingRow).addListener(listener)

		Platform.runLater {
			table.selectionModel.select(editingRow, column)
			table.focusModel.focus(editingRow, column)
			table.edit(editingRow, column)
		}

		do {
			Thread.sleep(100)
			time += 100

			if (time >= 5000) {
				state.value = CANCELLED
			}
		} while (state.value == EDITING)

		column.getCellObservableValue(editingRow).removeListener(listener)

		if (state.value == CANCELLED) {
			throw IllegalStateException("Editing has been cancelled in column '${column.text}' on row $editingRow")
		}
	}
}