package com.inventorysmartai.app.data.assistant.files;

import com.inventorysmartai.app.data.importing.parser.XlsxSaxReader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A set of sheets read from one file (an .xlsx, a CSV, or the tables found in OCR text). */
public final class WorkbookData {

    /** More sheets than this in one workbook are ignored (a report with hundreds of tabs is not something to chat about). */
    public static final int MAX_SHEETS = 20;

    private final List<SheetData> sheets;

    public WorkbookData(List<SheetData> sheets) {
        this.sheets = new ArrayList<SheetData>(sheets);
    }

    public List<SheetData> sheets() {
        return Collections.unmodifiableList(sheets);
    }

    public List<String> sheetNames() {
        List<String> names = new ArrayList<String>();
        for (SheetData s : sheets) {
            names.add(s.name);
        }
        return names;
    }

    public boolean isEmpty() {
        return sheets.isEmpty();
    }

    /**
     * Finds a sheet by name: exact, then ignoring case/diacritics, then a unique "contains". A null or blank name means
     * the first sheet. Returns null when nothing matches.
     */
    public SheetData sheet(String name) {
        if (sheets.isEmpty()) {
            return null;
        }
        if (name == null || CellValues.isBlank(name)) {
            return sheets.get(0);
        }
        for (SheetData s : sheets) {
            if (s.name.equals(name)) {
                return s;
            }
        }
        String wanted = CellValues.normalizeForSearch(name);
        for (SheetData s : sheets) {
            if (CellValues.normalizeForSearch(s.name).equals(wanted)) {
                return s;
            }
        }
        SheetData only = null;
        for (SheetData s : sheets) {
            if (CellValues.normalizeForSearch(s.name).contains(wanted)) {
                if (only != null) {
                    return null;
                }
                only = s;
            }
        }
        return only;
    }

    public static WorkbookData fromXlsx(byte[] bytes, XlsxSaxReader.SaxRunner sax) throws Exception {
        XlsxSaxReader reader = new XlsxSaxReader(sax);
        List<String> names = reader.listSheets(bytes);
        List<SheetData> out = new ArrayList<SheetData>();
        for (String name : names) {
            if (out.size() >= MAX_SHEETS) {
                break;
            }
            out.add(SheetData.fromTable(reader.readSheet(bytes, name)));
        }
        return new WorkbookData(out);
    }

    public static WorkbookData fromCsv(byte[] bytes, String sheetName) {
        List<SheetData> one = new ArrayList<SheetData>();
        one.add(TextTables.csvSheet(sheetName == null || sheetName.isEmpty() ? "البيانات" : sheetName, bytes));
        return new WorkbookData(one);
    }

    public static WorkbookData fromMarkdown(String text) {
        return new WorkbookData(TextTables.markdownTables(text));
    }
}
