package com.yukino.counter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Map;

public final class DashboardFrame extends JFrame {
    private record DateChoice(LocalDate date,String label){@Override public String toString(){return label;}}
    private final CounterService service;
    private final HeatmapPanel heatmap=new HeatmapPanel();
    private final JComboBox<DateChoice> dateChoice=new JComboBox<>();
    private final JLabel keyValue=metricValue("0");
    private final JLabel mouseValue=metricValue("0 m");
    private final JLabel periodLabel=new JLabel("累计总计");
    private final JLabel statusLabel=new JLabel("●  正在后台统计 · 数据仅保存在本机");

    public DashboardFrame(CounterService service){
        super("Yukino Counter");this.service=service;setIconImage(AppIcon.image(64));
        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);setMinimumSize(new Dimension(900,600));setSize(1080,700);setLocationRelativeTo(null);
        setContentPane(buildContent());new Timer(2000,e->refreshCounts()).start();refreshDates();refreshCounts();
    }

    private JComponent buildContent(){
        JPanel root=new FluentRoot(new BorderLayout(0,14));root.setBorder(new EmptyBorder(20,24,14,24));
        JPanel top=new JPanel();top.setOpaque(false);top.setLayout(new BoxLayout(top,BoxLayout.Y_AXIS));
        JPanel titleRow=new JPanel(new BorderLayout(18,0));titleRow.setOpaque(false);
        JPanel identity=new JPanel(new FlowLayout(FlowLayout.LEFT,12,0));identity.setOpaque(false);
        JLabel icon=new JLabel(new ImageIcon(AppIcon.image(42)));JPanel words=new JPanel();words.setOpaque(false);words.setLayout(new BoxLayout(words,BoxLayout.Y_AXIS));
        JLabel title=new JLabel("Yukino Counter");title.setFont(new Font("Segoe UI Variable Display",Font.BOLD,25));
        JLabel subtitle=new JLabel("键盘热力图与鼠标轨迹");subtitle.setForeground(new Color(82,91,102));words.add(title);words.add(subtitle);identity.add(icon);identity.add(words);titleRow.add(identity,BorderLayout.WEST);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT,8,2));actions.setOpaque(false);
        dateChoice.setPreferredSize(new Dimension(132,36));dateChoice.addActionListener(e->refreshCounts());
        JButton importButton=new JButton("导入 KM 数据");importButton.addActionListener(e->importKmData());
        JButton appearance=new JButton("个性化");appearance.addActionListener(e->showAppearanceDialog());
        JCheckBox startup=new JCheckBox("开机自启动",StartupManager.isEnabled());startup.setOpaque(false);startup.addActionListener(e->toggleStartup(startup));
        actions.add(dateChoice);actions.add(importButton);actions.add(appearance);actions.add(startup);titleRow.add(actions,BorderLayout.EAST);top.add(titleRow);top.add(Box.createVerticalStrut(14));
        JPanel metrics=new JPanel(new GridLayout(1,2,12,0));metrics.setOpaque(false);metrics.setMaximumSize(new Dimension(Integer.MAX_VALUE,88));
        metrics.add(metricCard("按键次数",keyValue,"次"));metrics.add(metricCard("鼠标移动",mouseValue,"按屏幕 DPI 估算"));top.add(metrics);root.add(top,BorderLayout.NORTH);

        AcrylicPanel keyboardCard=new AcrylicPanel(new BorderLayout(0,10));keyboardCard.setBorder(new EmptyBorder(14,14,14,14));
        JPanel sectionTitle=new JPanel(new BorderLayout());sectionTitle.setOpaque(false);JLabel keyboardLabel=new JLabel("完整键盘热力图");keyboardLabel.setFont(new Font("Microsoft YaHei UI",Font.BOLD,16));
        periodLabel.setForeground(new Color(91,99,110));sectionTitle.add(keyboardLabel,BorderLayout.WEST);sectionTitle.add(periodLabel,BorderLayout.EAST);keyboardCard.add(sectionTitle,BorderLayout.NORTH);keyboardCard.add(heatmap,BorderLayout.CENTER);root.add(keyboardCard,BorderLayout.CENTER);
        statusLabel.setForeground(new Color(36,125,74));root.add(statusLabel,BorderLayout.SOUTH);return root;
    }

    private static JLabel metricValue(String text){JLabel label=new JLabel(text);label.setFont(new Font("Segoe UI Variable Display",Font.BOLD,27));label.setForeground(new Color(22,32,43));return label;}
    private static JComponent metricCard(String title,JLabel value,String hint){
        AcrylicPanel card=new AcrylicPanel(new BorderLayout(16,0));card.setBorder(new EmptyBorder(12,18,12,18));
        JPanel words=new JPanel();words.setOpaque(false);words.setLayout(new BoxLayout(words,BoxLayout.Y_AXIS));JLabel name=new JLabel(title);name.setForeground(new Color(75,84,96));JLabel small=new JLabel(hint);small.setFont(small.getFont().deriveFont(11f));small.setForeground(new Color(110,119,130));words.add(name);words.add(Box.createVerticalStrut(3));words.add(small);card.add(words,BorderLayout.WEST);card.add(value,BorderLayout.EAST);return card;
    }

    private void refreshDates(){
        DateChoice selected=(DateChoice)dateChoice.getSelectedItem();dateChoice.removeAllItems();dateChoice.addItem(new DateChoice(null,"累计总计"));
        try{service.days().keySet().stream().sorted(Comparator.reverseOrder()).forEach(day->dateChoice.addItem(new DateChoice(day,day.equals(LocalDate.now())?"今天":day.format(DateTimeFormatter.ISO_DATE))));}
        catch(SQLException e){statusLabel.setText("读取日期失败："+e.getMessage());}
        if(selected!=null)for(int i=0;i<dateChoice.getItemCount();i++)if(java.util.Objects.equals(dateChoice.getItemAt(i).date(),selected.date())){dateChoice.setSelectedIndex(i);break;}
    }

    private void refreshCounts(){
        DateChoice choice=(DateChoice)dateChoice.getSelectedItem();if(choice==null)return;
        try{Map<Integer,Long> counts=service.snapshot(choice.date());heatmap.setCounts(counts);long total=counts.values().stream().mapToLong(Long::longValue).sum();keyValue.setText(String.format("%,d",total));mouseValue.setText(formatDistance(service.mouseDistance(choice.date())));periodLabel.setText(choice.label());}
        catch(SQLException e){statusLabel.setText("读取数据失败："+e.getMessage());}
    }

    private static String formatDistance(double meters){if(meters>=1000)return String.format("%.2f km",meters/1000);if(meters>=1)return String.format("%.1f m",meters);return String.format("%.1f cm",meters*100);}

    private void importKmData(){
        JFileChooser chooser=new JFileChooser();chooser.setDialogTitle("选择 KMCounter.ini");chooser.setFileFilter(new FileNameExtensionFilter("KM Counter 配置 (*.ini)","ini"));if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        Path file=chooser.getSelectedFile().toPath();statusLabel.setText("正在分析并导入 "+file.getFileName()+"…");
        new SwingWorker<KmCounterImporter.Parsed,Void>(){private CounterService.ImportStatus importStatus;
            @Override protected KmCounterImporter.Parsed doInBackground()throws Exception{var parsed=KmCounterImporter.parse(file);importStatus=service.importCounts(parsed.sha256(),file.getFileName().toString(),parsed.counts(),parsed.mouseDistances());return parsed;}
            @Override protected void done(){try{var parsed=get();if(importStatus==CounterService.ImportStatus.FULL){refreshDates();refreshCounts();statusLabel.setText("导入完成："+parsed.dayCount()+" 天，"+String.format("%,d",parsed.pressCount())+" 次按键，并包含鼠标距离");JOptionPane.showMessageDialog(DashboardFrame.this,"已合并 "+parsed.dayCount()+" 天、"+String.format("%,d",parsed.pressCount())+" 次按键及鼠标移动距离。"+(parsed.unassignedCount()>0?"\n其中 "+parsed.unassignedCount()+" 次没有具体键位，已计入总数。":""),"导入成功",JOptionPane.INFORMATION_MESSAGE);}else if(importStatus==CounterService.ImportStatus.MOUSE_ONLY){refreshCounts();statusLabel.setText("已为旧版导入记录补充鼠标移动距离");JOptionPane.showMessageDialog(DashboardFrame.this,"键盘数据没有重复累加，已补充历史鼠标移动距离。","升级完成",JOptionPane.INFORMATION_MESSAGE);}else{statusLabel.setText("该文件之前已经完整导入，本次未重复累加");JOptionPane.showMessageDialog(DashboardFrame.this,"这个文件已经完整导入过，没有重复累加。","无需导入",JOptionPane.INFORMATION_MESSAGE);}}catch(Exception e){Throwable cause=e.getCause()==null?e:e.getCause();statusLabel.setText("导入失败："+cause.getMessage());JOptionPane.showMessageDialog(DashboardFrame.this,cause.getMessage(),"导入失败",JOptionPane.ERROR_MESSAGE);}}
        }.execute();
    }

    private void toggleStartup(JCheckBox box){try{StartupManager.setEnabled(box.isSelected());statusLabel.setText(box.isSelected()?"已启用开机自启动":"已关闭开机自启动");}catch(Exception e){box.setSelected(!box.isSelected());JOptionPane.showMessageDialog(this,e.getMessage(),"设置失败",JOptionPane.ERROR_MESSAGE);}}

    private void showAppearanceDialog(){
        JDialog dialog=new JDialog(this,"个性化",true);JPanel panel=new JPanel(new GridBagLayout());panel.setBorder(new EmptyBorder(18,18,18,18));GridBagConstraints c=new GridBagConstraints();c.insets=new Insets(6,6,6,6);c.fill=GridBagConstraints.HORIZONTAL;c.weightx=1;
        JButton color=new JButton("选择热力颜色");color.addActionListener(e->{Color selected=JColorChooser.showDialog(dialog,"热力颜色",AppSettings.heatColor());if(selected!=null){AppSettings.heatColor(selected);heatmap.repaint();}});
        JButton image=new JButton("选择背景图片");image.addActionListener(e->chooseBackground(dialog));JButton clear=new JButton("清除背景");clear.addActionListener(e->{AppSettings.backgroundImage(null);heatmap.reloadBackground();});
        addRow(panel,c,0,"热力图",color);JPanel imageButtons=new JPanel(new FlowLayout(FlowLayout.LEFT,4,0));imageButtons.add(image);imageButtons.add(clear);addRow(panel,c,1,"背景图片",imageButtons);
        addSlider(panel,c,2,"背景透明度",0,100,AppSettings.backgroundOpacity(),AppSettings::backgroundOpacity);addSlider(panel,c,3,"背景缩放",50,200,AppSettings.backgroundZoom(),AppSettings::backgroundZoom);addSlider(panel,c,4,"水平位置",-500,500,AppSettings.backgroundOffsetX(),AppSettings::backgroundOffsetX);addSlider(panel,c,5,"垂直位置",-300,300,AppSettings.backgroundOffsetY(),AppSettings::backgroundOffsetY);
        JButton close=new JButton("完成");close.addActionListener(e->dialog.dispose());c.gridx=1;c.gridy=6;c.weightx=0;panel.add(close,c);dialog.setContentPane(panel);dialog.pack();dialog.setMinimumSize(new Dimension(520,390));dialog.setLocationRelativeTo(this);dialog.setVisible(true);
    }

    private interface IntSetter{void set(int value);}private void addSlider(JPanel panel,GridBagConstraints c,int row,String name,int min,int max,int value,IntSetter setter){JSlider slider=new JSlider(min,max,value);slider.addChangeListener(e->{setter.set(slider.getValue());heatmap.repaint();});addRow(panel,c,row,name,slider);}
    private static void addRow(JPanel panel,GridBagConstraints c,int row,String name,Component component){c.gridy=row;c.gridx=0;c.weightx=0;panel.add(new JLabel(name),c);c.gridx=1;c.weightx=1;panel.add(component,c);}
    private void chooseBackground(Component parent){JFileChooser chooser=new JFileChooser();chooser.setFileFilter(new FileNameExtensionFilter("图片 (*.png, *.jpg, *.jpeg, *.bmp, *.gif)","png","jpg","jpeg","bmp","gif"));if(chooser.showOpenDialog(parent)==JFileChooser.APPROVE_OPTION){AppSettings.backgroundImage(chooser.getSelectedFile().toPath());heatmap.reloadBackground();}}
    @Override public void setVisible(boolean visible){if(visible){refreshDates();refreshCounts();setState(Frame.NORMAL);toFront();}super.setVisible(visible);}

    private static final class FluentRoot extends JPanel{
        FluentRoot(LayoutManager layout){super(layout);setOpaque(true);} @Override protected void paintComponent(Graphics g){Graphics2D g2=(Graphics2D)g.create();g2.setPaint(new GradientPaint(0,0,new Color(238,245,251),getWidth(),getHeight(),new Color(246,242,250)));g2.fillRect(0,0,getWidth(),getHeight());g2.setColor(new Color(91,169,255,22));g2.fillOval(-180,-220,600,520);g2.setColor(new Color(194,139,255,18));g2.fillOval(getWidth()-420,getHeight()-360,620,520);g2.dispose();}
    }
    private static final class AcrylicPanel extends JPanel{
        AcrylicPanel(LayoutManager layout){super(layout);setOpaque(false);} @Override protected void paintComponent(Graphics g){Graphics2D g2=(Graphics2D)g.create();g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);Shape s=new RoundRectangle2D.Float(.5f,.5f,getWidth()-1,getHeight()-1,18,18);g2.setColor(new Color(255,255,255,178));g2.fill(s);g2.setColor(new Color(255,255,255,220));g2.draw(s);g2.dispose();super.paintComponent(g);}
    }
}
