package com.yukino.counter;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class HeatmapPanel extends JPanel {
    private record Key(int code, String label, float x, float y, float width, float height) {}
    private static final float GRID_WIDTH = 22.9f, GRID_HEIGHT = 6f;
    private static final List<Key> KEYS = List.of(
            key(27,"Esc",0,0), key(112,"F1",2,0), key(113,"F2",3,0), key(114,"F3",4,0), key(115,"F4",5,0),
            key(116,"F5",6.5f,0), key(117,"F6",7.5f,0), key(118,"F7",8.5f,0), key(119,"F8",9.5f,0),
            key(120,"F9",11,0), key(121,"F10",12,0), key(122,"F11",13,0), key(123,"F12",14,0),
            key(44,"PrtSc",15.4f,0), key(145,"Scroll",16.4f,0), key(19,"Pause",17.4f,0),
            key(192,"`",0,1),key('1',1,1),key('2',2,1),key('3',3,1),key('4',4,1),key('5',5,1),key('6',6,1),key('7',7,1),key('8',8,1),key('9',9,1),key('0',10,1),key(189,"-",11,1),key(187,"=",12,1),key(8,"Backspace",13,1,2,1),
            key(45,"Insert",15.4f,1),key(36,"Home",16.4f,1),key(33,"PgUp",17.4f,1),key(144,"Num",18.9f,1),key(111,"/",19.9f,1),key(106,"*",20.9f,1),key(109,"-",21.9f,1),
            key(9,"Tab",0,2,1.5f,1),key('Q',1.5f,2),key('W',2.5f,2),key('E',3.5f,2),key('R',4.5f,2),key('T',5.5f,2),key('Y',6.5f,2),key('U',7.5f,2),key('I',8.5f,2),key('O',9.5f,2),key('P',10.5f,2),key(219,"[",11.5f,2),key(221,"]",12.5f,2),key(220,"\\",13.5f,2,1.5f,1),
            key(46,"Delete",15.4f,2),key(35,"End",16.4f,2),key(34,"PgDn",17.4f,2),key(103,"7",18.9f,2),key(104,"8",19.9f,2),key(105,"9",20.9f,2),key(107,"+",21.9f,2,1,2),
            key(20,"Caps",0,3,1.8f,1),key('A',1.8f,3),key('S',2.8f,3),key('D',3.8f,3),key('F',4.8f,3),key('G',5.8f,3),key('H',6.8f,3),key('J',7.8f,3),key('K',8.8f,3),key('L',9.8f,3),key(186,";",10.8f,3),key(222,"'",11.8f,3),key(13,"Enter",12.8f,3,2.2f,1),
            key(100,"4",18.9f,3),key(101,"5",19.9f,3),key(102,"6",20.9f,3),
            key(160,"Shift",0,4,2.3f,1),key('Z',2.3f,4),key('X',3.3f,4),key('C',4.3f,4),key('V',5.3f,4),key('B',6.3f,4),key('N',7.3f,4),key('M',8.3f,4),key(188,",",9.3f,4),key(190,".",10.3f,4),key(191,"/",11.3f,4),key(161,"Shift",12.3f,4,2.7f,1),
            key(38,"↑",16.4f,4),key(97,"1",18.9f,4),key(98,"2",19.9f,4),key(99,"3",20.9f,4),key(KeyNames.NUMPAD_ENTER,"Enter",21.9f,4,1,2),
            key(162,"Ctrl",0,5,1.3f,1),key(91,"Win",1.3f,5,1.2f,1),key(164,"Alt",2.5f,5,1.2f,1),key(32,"Space",3.7f,5,6.1f,1),key(165,"Alt",9.8f,5,1.2f,1),key(92,"Win",11,5,1.2f,1),key(93,"Menu",12.2f,5,1.2f,1),key(163,"Ctrl",13.4f,5,1.6f,1),
            key(37,"←",15.4f,5),key(40,"↓",16.4f,5),key(39,"→",17.4f,5),key(96,"0",18.9f,5,2,1),key(110,".",20.9f,5)
    );

    private Map<Integer, Long> counts = Map.of();
    private BufferedImage background;

    public HeatmapPanel() {
        setPreferredSize(new Dimension(1180, 430));
        setMinimumSize(new Dimension(820, 330));
        setOpaque(false);
        reloadBackground();
    }

    private static Key key(int code, String label, float x, float y) { return key(code,label,x,y,1,1); }
    private static Key key(int code, float x, float y) { return key(code,KeyNames.name(code),x,y,1,1); }
    private static Key key(int code, String label, float x, float y, float width, float height) { return new Key(code,label,x,y,width,height); }
    public void setCounts(Map<Integer, Long> counts) { this.counts = Map.copyOf(counts); repaint(); }

    public void reloadBackground() {
        background=null;
        Path path=AppSettings.backgroundImage();
        if(path!=null&&Files.isRegularFile(path)) try{background=ImageIO.read(path.toFile());}catch(IOException ignored){}
        repaint();
    }

    @Override protected void paintComponent(Graphics original) {
        Graphics2D g=(Graphics2D)original.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        Shape clip=new RoundRectangle2D.Float(0,0,getWidth()-1,getHeight()-1,22,22);
        g.clip(clip);g.setColor(new Color(221,231,242));g.fillRect(0,0,getWidth(),getHeight());paintBackground(g);
        g.setColor(new Color(10,35,60,background==null?16:72));g.fillRect(0,0,getWidth(),getHeight());
        long max=counts.entrySet().stream().filter(e->e.getKey()!=KmCounterImporter.UNASSIGNED_KEY).mapToLong(Map.Entry::getValue).max().orElse(1);
        float margin=18f,unit=Math.min((getWidth()-margin*2)/GRID_WIDTH,(getHeight()-margin*2)/GRID_HEIGHT);
        float left=(getWidth()-unit*GRID_WIDTH)/2f,top=(getHeight()-unit*GRID_HEIGHT)/2f;
        for(Key key:KEYS)paintKey(g,key,left+key.x*unit,top+key.y*unit,key.width*unit-5,key.height*unit-5,max);
        g.setClip(null);g.setColor(new Color(255,255,255,145));g.setStroke(new BasicStroke(1));g.draw(clip);g.dispose();
    }

    private void paintBackground(Graphics2D g) {
        if(background==null)return;
        double base=Math.max((double)getWidth()/background.getWidth(),(double)getHeight()/background.getHeight());
        double scale=base*AppSettings.backgroundZoom()/100d;int w=(int)(background.getWidth()*scale),h=(int)(background.getHeight()*scale);
        int x=(getWidth()-w)/2+AppSettings.backgroundOffsetX(),y=(getHeight()-h)/2+AppSettings.backgroundOffsetY();
        g.setComposite(AlphaComposite.SrcOver.derive(AppSettings.backgroundOpacity()/100f));g.drawImage(background,x,y,w,h,null);g.setComposite(AlphaComposite.SrcOver);
    }

    private void paintKey(Graphics2D g,Key key,float x,float y,float w,float h,long max){
        long value=counts.getOrDefault(key.code,0L);
        if(key.code==160||key.code==161)value+=counts.getOrDefault(16,0L)/2;
        if(key.code==162||key.code==163)value+=counts.getOrDefault(17,0L)/2;
        if(key.code==164||key.code==165)value+=counts.getOrDefault(18,0L)/2;
        double normalized=value==0?0:Math.log1p(value)/Math.log1p(max);Color heat=AppSettings.heatColor();
        Shape shape=new RoundRectangle2D.Float(x,y,w,h,Math.min(12,h*.25f),Math.min(12,h*.25f));
        g.setColor(value==0?new Color(247,250,253,135):new Color(heat.getRed(),heat.getGreen(),heat.getBlue(),105+(int)(135*normalized)));g.fill(shape);
        g.setColor(value==0?new Color(255,255,255,190):new Color(255,255,255,130));g.draw(shape);
        g.setColor(value==0?new Color(31,41,55):Color.WHITE);float fontSize=Math.max(8.5f,Math.min(13f,h/4.1f));
        g.setFont(new Font("Segoe UI",Font.BOLD,Math.round(fontSize)));FontMetrics fm=g.getFontMetrics();g.drawString(key.label,x+(w-fm.stringWidth(key.label))/2,y+h*(value>0?.43f:.58f));
        if(value>0){String count=compact(value);g.setFont(new Font("Segoe UI",Font.PLAIN,Math.max(8,Math.round(fontSize-2))));fm=g.getFontMetrics();g.drawString(count,x+(w-fm.stringWidth(count))/2,y+h*.76f);}
    }

    private static String compact(long value){if(value>=1_000_000)return String.format("%.1fm",value/1_000_000d);if(value>=10_000)return String.format("%.1fk",value/1_000d);return Long.toString(value);}
}
