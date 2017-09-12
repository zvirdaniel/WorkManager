package com.duno.workmanager.Controllers

import com.duno.workmanager.Data.VisibleData
import com.duno.workmanager.Data.WorkSession
import com.duno.workmanager.Models.ObservableSession
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.TableColumn
import javafx.scene.control.TableRow
import javafx.scene.control.TableView
import javafx.scene.control.cell.TextFieldTableCell
import javafx.scene.input.KeyCode
import javafx.util.Callback
import java.net.URL
import java.util.*

class MonthController : Initializable {
    @FXML
    var table = TableView<ObservableSession>()
    @FXML
    var date = TableColumn<ObservableSession, String>()
    @FXML
    var time = TableColumn<ObservableSession, String>()
    @FXML
    var duration = TableColumn<ObservableSession, String>()
    @FXML
    var description = TableColumn<ObservableSession, String>()

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        deleteKeyHandler()

        blankRowCallback()

        cellValueFactories()

        cellFactories()

        commitHandlers()

        VisibleData.addTabController(this)
    }

    private fun deleteKeyHandler() {
        table.onKeyPressed = EventHandler {
            val currentRow = table.selectionModel.selectedItem
            if (currentRow != null) {
                if (it.code == KeyCode.DELETE) {
                    removeRow(currentRow)
                }
            }
        }
    }

    private fun commitHandlers() {
        date.onEditCommit = EventHandler { it.rowValue.beginDateString = it.newValue }
        time.onEditCommit = EventHandler { it.rowValue.beginTimeString = it.newValue }
        duration.onEditCommit = EventHandler { it.rowValue.durationString = it.newValue }
        description.onEditCommit = EventHandler { it.rowValue.descriptionString = it.newValue }
    }

    private fun cellFactories() {
        date.cellFactory = TextFieldTableCell.forTableColumn()
        time.cellFactory = TextFieldTableCell.forTableColumn()
        duration.cellFactory = TextFieldTableCell.forTableColumn()
        description.cellFactory = TextFieldTableCell.forTableColumn()
    }

    private fun cellValueFactories() {
        date.setCellValueFactory { it.value.beginDateProperty }
        time.setCellValueFactory { it.value.beginTimeProperty }
        duration.setCellValueFactory { it.value.durationProperty }
        description.setCellValueFactory { it.value.descriptionProperty }
    }

    private fun blankRowCallback() {
        table.rowFactory = Callback {
            val row = TableRow<ObservableSession>()

            row.onMouseClicked = EventHandler {
                if (it.clickCount >= 2) {
                    newRowHandler(row)
                }
            }

            row
        }
    }

    fun removeRow(row: ObservableSession) {
        table.items.remove(row)
    }

    fun createNewRow() {
        val lastSession = table.items.lastOrNull() ?: WorkSession(addMinutes = 30, hourlyWage = 0, description = "Doplnit!")
        val session = WorkSession(addMinutes = 30, hourlyWage = lastSession.hourlyWage, description = lastSession.description)
        table.items.add(ObservableSession(session))
    }

    private fun newRowHandler(row: TableRow<ObservableSession>) {
        if (row.item !is ObservableSession) {
            createNewRow()
        }
    }
}