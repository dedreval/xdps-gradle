package com.wiley.cms.cochrane.utils;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.tes.util.Extensions;
import jxl.CellView;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.format.Alignment;
import jxl.format.Border;
import jxl.format.BorderLineStyle;
import jxl.format.Colour;
import jxl.format.VerticalAlignment;
import jxl.write.Label;
import jxl.write.WritableCellFormat;
import jxl.write.WritableFont;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 25.03.13
 */
public class XLSHelper {
    public static final float WIDE_FACTOR_1_5 = 1.5f;
    public static final int   WIDE_FACTOR_2 = 2;
    public static final int   WIDE_FACTOR_3 = 3;
    public static final int   WIDE_FACTOR_4 = 4;
    public static final float WIDE_FACTOR_7 = 7;
    public static final int   WIDE_FACTOR_15 = 15;

    private static final String DEFAULT_SHEET_NAME = "Sheet1";
    private static final int DEFAULT_COL_SIZE = 2560;
    private static final int BIG_FONT_FACTOR = 3;

    private static final Map<Color, Colour> EQUAL_COLORS = new HashMap<>();

    private final WritableWorkbook workbook;

    private WritableSheet curSheet;

    private final WritableCellFormat arialBoldFormat;
    private final WritableCellFormat defaultFormat;
    private final WritableFont arial;
    private final WritableFont arialBold;

    private WritableFont arialBigBold = null;
    private Map<Color, Colour> adaptedColors = null;
    private List<WritableCellFormat> formats = null;
    private Set<Colour> changeableColors = null;

    private Map<String, int[]> title2SheetColumnMap = new HashMap<>();
    private int maxSheetCount = -1;
   
    static {
        EQUAL_COLORS.put(Color.WHITE, Colour.WHITE);
        EQUAL_COLORS.put(Color.BLACK, Colour.BLACK);
        EQUAL_COLORS.put(Color.GRAY, Colour.GRAY_50);
        //EQUAL_COLORS.put(Color.PINK, Colour.ROSE);
        EQUAL_COLORS.put(Color.ORANGE, Colour.LIGHT_ORANGE);
        EQUAL_COLORS.put(Color.YELLOW, Colour.YELLOW);
    }

    public XLSHelper(File file) throws Exception {
        this(file, DEFAULT_SHEET_NAME);
    }

    public XLSHelper(File file, String mainSheetName) throws Exception {
        WorkbookSettings wbs = new WorkbookSettings();
        wbs.setCellValidationDisabled(true);
        workbook = Workbook.createWorkbook(file, wbs);

        addCurrentSheet(mainSheetName);

        arialBold = new WritableFont(WritableFont.ARIAL, WritableFont.DEFAULT_POINT_SIZE,
            WritableFont.BOLD, false);
        arialBoldFormat = new WritableCellFormat(arialBold);

        arial = new WritableFont(WritableFont.ARIAL, WritableFont.DEFAULT_POINT_SIZE,
            WritableFont.NO_BOLD, false);
        defaultFormat = new WritableCellFormat(arial);
        defaultFormat.setWrap(true);
        defaultFormat.setVerticalAlignment(jxl.format.VerticalAlignment.TOP);
    }

    public XLSHelper(File file, String mainSheetName, boolean useExtraFormats, int maxSheetCount) throws Exception {
        this(file, mainSheetName);
        if (useExtraFormats) {
            initChangeableColors();
            setExtraFonts();
        }
        this.maxSheetCount = maxSheetCount;
    }

    public void closeAndSaveToRepository(File tempFile, String path, boolean keepOld, IRepository rp)
            throws Exception {
        closeAndSaveToRepository(tempFile, null, path, keepOld, rp);
    }

    public void closeAndSaveToRepository(File tempFile, File csvFile, String path, boolean keepOld, IRepository rp)
            throws Exception {
        close();
        String postfix = null;
        if (keepOld && rp.isFileExists(path)) {
            postfix = Instant.ofEpochMilli(
                new File(rp.getRealFilePath(path)).lastModified()).toString().replaceAll(":", "-").replaceAll("Z", "");
            rp.putFile(path.replace(Extensions.XLS, postfix + Extensions.XLS), rp.getFile(path));
        }
        rp.putFile(path,  new FileInputStream(tempFile));

        if (csvFile != null) {
            String csvPath = path.replace(Extensions.XLS, Extensions.CSV).replace("CochraneReport", "entire");
            if (postfix != null && rp.isFileExists(csvPath)) {
                rp.putFile(csvPath.replace(Extensions.CSV, postfix + Extensions.CSV), rp.getFile(csvPath));
            }
            rp.putFile(csvPath, new FileInputStream(csvFile));
        }
    }

    private void initChangeableColors() {

        formats = new ArrayList<>();

        adaptedColors = new HashMap<>();

        changeableColors = new HashSet<>();
        changeableColors.add(Colour.DARK_BLUE2);
        changeableColors.add(Colour.LIGHT_TURQUOISE2);
        changeableColors.add(Colour.PINK2);
        changeableColors.add(Colour.PLUM2);
        changeableColors.add(Colour.PINK);
        changeableColors.add(Colour.PLUM);
        changeableColors.add(Colour.DARK_RED2);
        changeableColors.add(Colour.TEAL2);
        changeableColors.add(Colour.BLUE2);
        changeableColors.add(Colour.TURQOISE2);
        changeableColors.add(Colour.VIOLET2);
        changeableColors.add(Colour.RED);
        changeableColors.add(Colour.VIOLET);
        changeableColors.add(Colour.BLUE);
        changeableColors.add(Colour.PERIWINKLE);
    }

    private void setExtraFonts() {
        arialBigBold = createBigFont();
    }

    private WritableFont createBigFont() {
        return new WritableFont(WritableFont.ARIAL, WritableFont.DEFAULT_POINT_SIZE
                + WritableFont.DEFAULT_POINT_SIZE / BIG_FONT_FACTOR, WritableFont.BOLD, false);
    }

    private WritableFont createFont() {
        return new WritableFont(WritableFont.ARIAL);
    }

    public void addCurrentSheet(String sheetName, int ind) {

        curSheet = getSheet(ind);
        if (curSheet == null) {
            curSheet = workbook.createSheet(sheetName, ind);
        } else {
            curSheet.setName(sheetName);
        }
    }

    public int addCurrentSheet(String sheetName) {

        WritableSheet[] sheets = workbook.getSheets();
        int ret = sheets.length;
        curSheet = workbook.createSheet(sheetName, ret);
        return ret;
    }

    public void setCurrentSheet(int ind) {

        WritableSheet sheet = getSheet(ind);
        if (sheet != null) {
            curSheet = sheet;
        }
    }

    public void mergeRow(int row, int size) throws Exception {
        mergeCells(0, row, size, row);
    }

    public void wideColumn(int col, int width) throws Exception {

        CellView view = new CellView();
        view.setSize(width);
        curSheet.setColumnView(col, view);
    }

    private void setColumn(int col, int wideFactor, WritableCellFormat format) throws Exception {

        CellView view = new CellView();
        if (wideFactor > 0) {
            view.setSize(DEFAULT_COL_SIZE * wideFactor);
        }  else {
            view.setSize(curSheet.getColumnView(col).getSize());
        }
        view.setFormat(format);
        curSheet.setColumnView(col, view);
    }

    public void setColumn(int col, int wideFactor) throws Exception {
        setColumn(col, wideFactor, defaultFormat);
    }

    public int registerTitleColumn(int col, int wideFactor, String title, int sheet, Object... values)
            throws Exception {
        return registerTitleColumn(col, wideFactor, defaultFormat, title, sheet, values);
    }

    public int registerTitleColumn(int col, int wideFactor, int formatId, String title, int sheet, Object... values)
            throws Exception {
        return registerTitleColumn(col, wideFactor, formats.get(formatId), title, sheet, values);
    }

    private int registerTitleColumn(int col, int wideFactor, WritableCellFormat format, String title, int sheet,
                                   Object... values) throws Exception {
        setColumn(col, wideFactor, format);
        int row = 0;
        addTitle(title, col, row++);
        int[] sheetColumns = title2SheetColumnMap.computeIfAbsent(title, f -> new int[maxSheetCount]);
        sheetColumns[sheet] = col;
        for (Object value: values) {
            addValue(value.toString(), col, row++);
        }
        return row;
    }

    public void addTitle(String title, int col) throws Exception {
        addTitle(title, col, 0);
    }

    public void addValue(String value, String columnName, int row, int formatId, int page) throws Exception {
        addValue(value, title2SheetColumnMap.get(columnName)[page], row, formats.get(formatId));
    }

    public void addValue(String value, String columnName, int row, int page) throws Exception {
        addValue(value, title2SheetColumnMap.get(columnName)[page], row);
    }

    public void addValue(String value, int col, int row) throws Exception {
        addValue(value, col, row, defaultFormat);
    }

    public void addTitle(String title, int col, int row) throws Exception {
        addValue(title, col, row, arialBoldFormat);
    }

    public int registerFormat(Alignment alignment) throws Exception {
        return registerFormat(createNewFormat(defaultFormat.getBackgroundColour(), arial, alignment));
    }

    public int registerFormat(Color colorF, Color colorB, boolean bigFont, boolean boldFont,
        boolean centre) throws Exception {

        WritableFont font = bigFont ? arialBigBold : (boldFont ? arialBold : arial);
        if (colorF != null) {
            Colour newF = findColour(colorF);
            font = bigFont ? createBigFont() : createFont();
            font.setColour(newF);
        }
        return registerFormat(createNewFormat(findColour(colorB), font, centre ? Alignment.CENTRE : Alignment.LEFT));
    }

    private int registerFormat(WritableCellFormat format) {
        formats.add(format);
        return formats.size() - 1;
    }

    public void resetFormat(int rowCount) throws Exception {
        for (int i = 0; i < rowCount; i++) {
            setColumn(i, -1);
        }
    }

    public void addValue(String value, int col, int row, int formatId) throws Exception {
        addValue(value, col, row, formats.get(formatId));
    }

    public void addValue(String value, int col, int row, float wideFactor, int formatId) throws Exception {
        WritableCellFormat format = formats.get(formatId);
        wideColumn(col, (int) (wideFactor * (float) DEFAULT_COL_SIZE));
        addValue(value, col, row, format);
    }

    public void close() throws Exception {

        workbook.write();
        workbook.close();

        if (adaptedColors != null) {
            adaptedColors.clear();
        }

        if (changeableColors != null) {
            changeableColors.clear();
        }

        if (formats != null) {
            formats.clear();
        }
    }

    public void mergeCells(int startCol, int startRow, int endCol, int endRow) throws Exception {
        curSheet.mergeCells(startCol, startRow, endCol, endRow);
    }

    public WritableSheet getSheet(int ind) {

        WritableSheet[] sheets = workbook.getSheets();
        if (sheets.length > ind) {
            return sheets[ind];
        }

        return null;
    }

    private void addValue(String value, int col, int row, WritableCellFormat format) throws Exception {
        Label label = new Label(col, row, value, format);
        curSheet.addCell(label);
    }

    private WritableCellFormat createNewFormat(Colour colorB, WritableFont font, Alignment alignment) throws Exception {

        WritableCellFormat ret = new WritableCellFormat(font);
        
        ret.setWrap(true);
        ret.setVerticalAlignment(VerticalAlignment.CENTRE);
        ret.setAlignment(alignment);
        ret.setBackground(colorB);
        ret.setBorder(Border.ALL, BorderLineStyle.THIN);
       
        return ret;
    }

    private Colour findColour(Color color) throws Exception {
        Colour ret =  EQUAL_COLORS.get(color);
        if (ret != null) {
            return ret;
        }
        ret = stealChangeableColor();
        if (ret == null) {
            throw new Exception("cannot set the required color because there are no free changeable colors in JXL");
        }
        adaptedColors.put(color, ret);
        workbook.setColourRGB(ret, color.getRed(), color.getGreen(), color.getBlue());
        return ret;
    }

    private Colour stealChangeableColor() {
        if (!changeableColors.isEmpty()) {

            Colour ret = changeableColors.iterator().next();
            changeableColors.remove(ret);
            return ret;
        }
        return null;
    }
}
