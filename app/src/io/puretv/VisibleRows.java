package io.puretv;

/** Exclusive end row for the viewport plus one guard row. Prepared artwork is retained independently. */
final class VisibleRows {
    private VisibleRows() {}
    static int end(float top,float rowHeight,float translation,float viewportHeight){
        if(rowHeight<=0)return 0;
        return Math.max(0,(int)Math.floor((viewportHeight-translation-top)/rowHeight)+2);
    }
}
