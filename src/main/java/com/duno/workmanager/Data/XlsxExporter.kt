package com.duno.workmanager.Data

import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.ss.util.RegionUtil
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Package: com.duno.workmanager.Data
 * Created by Daniel Zvir on 25.5.17.
 */

/**
 * Generates XLSX spreadsheet and saves it into a file.
 *
 * @param month Month between 1 and 12, will create one sheet with specified month
 * @param saveFile File to save all data into, example: result.xlsx
 */
fun WorkYear.writeYearInXlsx(saveFile: File, month: Int) {
    if (month < 1 || month > 12) {
        throw IllegalArgumentException("Month must be between 1 and 12!")
    }
    val range = month..month
    writeYearInXlsx(saveFile, range)
}

/**
 * Generates XLSX spreadsheet and saves it into a file.
 *
 * @param monthRange Range between 1 and 12, each month will be a separate sheet
 * @param saveFile File to save all data into, example: result.xlsx
 * @return true if successfully written into a file, false if IOException was thrown
 */
fun WorkYear.writeYearInXlsx(saveFile: File, monthRange: IntRange): Boolean {
    if (monthRange.start < 1 || monthRange.endInclusive > 12) {
        throw IllegalArgumentException("Month range is between 1 and 12!")
    }

    val titles = arrayOf("Datum", "Začátek práce", "Hodiny", "Popis práce")
    val monthsCzech = arrayOf("Leden", "Únor", "Březen", "Duben", "Květen",
            "Červen", "Červenec", "Srpen", "Září", "Říjen", "Listopad", "Prosinec")
    val dataMap = getMapOfRawWorkSessions()
    val wb = XSSFWorkbook()

    for (monthNumber in monthRange) {
        val month = dataMap[monthNumber]
        val monthName = monthsCzech[monthNumber - 1]

        val sheet = wb.createSheet() // Sheet with current month
        wb.setSheetName(wb.getSheetIndex(sheet), monthName) // Set sheet name
        sheet.printSetup.landscape = true
        sheet.fitToPage = true
        sheet.horizontallyCenter = true

        val titleRow = sheet.createRow(0) // Row 0
        titleRow.setHeightInPoints(45F)
        val titleCell = titleRow.createCell(0)
        titleCell.setCellValue(monthName) // Set month string to cell
        titleCell.setCellStyle(createMonthNameStyle(wb)) // Set styling to cell

        // Border
        val region = CellRangeAddress.valueOf("\$A$1:\$D$1")
        sheet.addMergedRegion(region) // Make cell 0 expand
        val border = BorderStyle.THIN
        val borderColor = IndexedColors.BLACK.index.toInt()
        RegionUtil.setBorderBottom(border, region, sheet)
        RegionUtil.setBottomBorderColor(borderColor, region, sheet)
        RegionUtil.setBorderTop(border, region, sheet)
        RegionUtil.setTopBorderColor(borderColor, region, sheet)
        RegionUtil.setBorderLeft(border, region, sheet)
        RegionUtil.setLeftBorderColor(borderColor, region, sheet)
        RegionUtil.setBorderRight(border, region, sheet)
        RegionUtil.setRightBorderColor(borderColor, region, sheet)

        // Header for every row
        val headerRow = sheet.createRow(1) // Row 1
        headerRow.setHeightInPoints(40F)
        for (i in titles.indices) {
            val headerCell = headerRow.createCell(i)
            headerCell.setCellValue(titles[i]) // Set title string to cell
            headerCell.setCellStyle(createHeaderStyle(wb)) // Set styling to cell
        }

        // Create cells with for data
        for (rowNumber in 2..26) { // Maximum 25 sessions allowed, do not work too hard!
            val row = sheet.createRow(rowNumber)
            for (column in titles.indices) {
                row.createCell(column)
            }
        }

        // Parse data into all cells
        if (month?.size != 0) {
            for (dataRowNumber in month!!.indices) {
                val row = sheet.getRow(dataRowNumber + 2)
                val session = WorkSession(month[dataRowNumber])
                val localDate = session.beginDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                // Begin date
                row.getCell(0).setCellValue(localDate.format(DateTimeFormatter.ofPattern("dd. L. u")))
                // Begin hours
                row.getCell(1).setCellValue(SimpleDateFormat("HH:mm").format(session.beginDate))
                // Duration in hours
                val bigDecimal = BigDecimal((session.durationInMinutes / 60.0)).setScale(1, RoundingMode.HALF_UP)
                row.getCell(2).setCellValue(bigDecimal.toDouble())
                // Description
                row.getCell(3).setCellValue(session.description)
            }
        }

        // Style cells with data
        for (rowNumber in 2..26) {
            val row = sheet.getRow(rowNumber)
            for (column in titles.indices) {
                val cell = row.getCell(column)
                var cellStyle: XSSFCellStyle
                when (column) {
                    3 -> cellStyle = createDescriptionStyle(wb)
                    2 -> {
                        cellStyle = createDataStyle(wb)
                        cellStyle.setDataFormat(wb.createDataFormat().getFormat("0.#"))
                    }
                    else -> cellStyle = createDataStyle(wb)
                }
                cell.cellStyle = cellStyle
            }
        }

        // Formula with total hours calculation
        val formulaRow = sheet.createRow(28)
        var cell = formulaRow.createCell(0)
        cell.setCellValue("Celkový počet hodin: ")
        cell.cellStyle = createDataStyle(wb)
        cell.cellStyle.font.bold = true

        cell = formulaRow.createCell(1)
        cell.cellFormula = "SUM(C3:C27)"
        cell.cellStyle = createDataStyle(wb)
        cell.cellStyle.setDataFormat(wb.createDataFormat().getFormat("0.##"))
        cell.cellStyle.font.italic = true

        //finally set column widths, the width is measured in units of 1/256th of a character width
        sheet.setColumnWidth(0, 30 * 256) //30 characters wide
        sheet.setColumnWidth(3, 90 * 256)
    }

    // Write the output to a file
    try {
        wb.write(saveFile.outputStream())
    } catch (e: IOException) {
        return false
    }

    return true
}

private fun createDescriptionStyle(wb: XSSFWorkbook): XSSFCellStyle {
    val style = wb.createCellStyle()

    // Font
    val descriptionFont = wb.createFont()
    descriptionFont.fontHeightInPoints = 10.toShort()
    style.setFont(descriptionFont)

    // Border
    style.setBorderTop(BorderStyle.THIN)
    style.setBorderBottom(BorderStyle.THIN)
    style.setBorderLeft(BorderStyle.THIN)
    style.setBorderRight(BorderStyle.THIN)
    style.topBorderColor = IndexedColors.BLACK.getIndex()
    style.bottomBorderColor = IndexedColors.BLACK.getIndex()
    style.leftBorderColor = IndexedColors.BLACK.getIndex()
    style.rightBorderColor = IndexedColors.BLACK.getIndex()

    // Alignment
    style.setAlignment(HorizontalAlignment.LEFT)
    style.setVerticalAlignment(VerticalAlignment.CENTER)
    return style
}

private fun createDataStyle(wb: XSSFWorkbook): XSSFCellStyle {
    val style = wb.createCellStyle()

    // Font
    val font = wb.createFont()
    font.fontHeightInPoints = 10.toShort()
    style.setFont(font)

    // Border
    style.setBorderTop(BorderStyle.THIN)
    style.setBorderBottom(BorderStyle.THIN)
    style.setBorderLeft(BorderStyle.THIN)
    style.setBorderRight(BorderStyle.THIN)
    style.topBorderColor = IndexedColors.BLACK.getIndex()
    style.bottomBorderColor = IndexedColors.BLACK.getIndex()
    style.leftBorderColor = IndexedColors.BLACK.getIndex()
    style.rightBorderColor = IndexedColors.BLACK.getIndex()

    // Alignment
    style.setVerticalAlignment(VerticalAlignment.CENTER)
    style.setAlignment(HorizontalAlignment.CENTER)

    // Other
    style.wrapText = true
    return style
}

private fun createHeaderStyle(wb: XSSFWorkbook): XSSFCellStyle {
    val style = wb.createCellStyle()

    // Font
    val dateFont = wb.createFont()
    dateFont.fontHeightInPoints = 11.toShort()
    dateFont.color = IndexedColors.WHITE.getIndex()
    style.setFont(dateFont)

    // Border
    style.setBorderTop(BorderStyle.THIN)
    style.setBorderBottom(BorderStyle.THIN)
    style.setBorderLeft(BorderStyle.THIN)
    style.setBorderRight(BorderStyle.THIN)
    style.topBorderColor = IndexedColors.BLACK.getIndex()
    style.bottomBorderColor = IndexedColors.BLACK.getIndex()
    style.leftBorderColor = IndexedColors.BLACK.getIndex()
    style.rightBorderColor = IndexedColors.BLACK.getIndex()

    // Alignment
    style.setAlignment(HorizontalAlignment.CENTER)
    style.setVerticalAlignment(VerticalAlignment.CENTER)

    // Other
    style.fillForegroundColor = IndexedColors.GREY_50_PERCENT.getIndex()
    style.setFillPattern(FillPatternType.SOLID_FOREGROUND)
    style.wrapText = true
    return style
}

private fun createMonthNameStyle(wb: XSSFWorkbook): XSSFCellStyle {
    val style = wb.createCellStyle()

    // Font
    val titleFont = wb.createFont()
    titleFont.fontHeightInPoints = 18.toShort()
    titleFont.bold = true
    style.setFont(titleFont)

    // Alignment
    style.setAlignment(HorizontalAlignment.CENTER)
    style.setVerticalAlignment(VerticalAlignment.CENTER)
    return style
}