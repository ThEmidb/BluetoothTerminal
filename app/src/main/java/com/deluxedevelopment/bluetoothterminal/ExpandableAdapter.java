package com.deluxedevelopment.bluetoothterminal;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseExpandableListAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.button.MaterialButton; // Material
import java.util.List;


public class ExpandableAdapter extends BaseExpandableListAdapter {

    private final MainActivity mainActivity;
    private final Context context;
    private final List<String> groupTitles;     // ["Connected Devices", "Available Devices"]
    private final List<String> group1Data;      // connected devices
    private final List<String> group2Data;      // available devices

    private boolean isScanning = false;
    private int foundCount = 0;

    public ExpandableAdapter(MainActivity mainActivity,
                             List<String> groupTitles,
                             List<String> group1Data,
                             List<String> group2Data) {
        this.context = mainActivity;
        this.mainActivity = mainActivity;
        this.groupTitles = groupTitles;
        this.group1Data = group1Data;
        this.group2Data = group2Data;
    }

    public void setScanning(boolean scanning) {
        this.isScanning = scanning;
        notifyDataSetChanged();
    }

    public void setFoundCount(int count) {
        this.foundCount = count;
        notifyDataSetChanged();
    }
    // --- Required counts/IDs ---

    @Override public int getGroupCount() { return groupTitles.size(); }
    @Override public int getChildrenCount(int groupPosition) { return 1; } // one child layout per group
    @Override public Object getGroup(int groupPosition) { return groupTitles.get(groupPosition); }
    @Override public Object getChild(int groupPosition, int childPosition) { return null; }
    @Override public long getGroupId(int groupPosition) { return groupPosition; }
    @Override public long getChildId(int groupPosition, int childPosition) { return childPosition; }
    @Override public boolean hasStableIds() { return false; }
    @Override public int getChildTypeCount() { return 2; }
    @Override public int getChildType(int groupPosition, int childPosition) { return groupPosition == 0 ? 0 : 1; }

    // --- Group header with single right arrow that rotates ---

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.group_header, parent, false);
        }

        TextView title = convertView.findViewById(R.id.groupTitle);
        ImageView arrow = convertView.findViewById(R.id.groupArrow); // ImageView in group_header.xml using @drawable/ic_arrow_right
        ProgressBar headerProgress = convertView.findViewById(R.id.headerProgress);

        int count;
        if(groupPosition == 0){
            count = group1Data.size();
        } else {
            count = group2Data.size();
        }

        title.setText(groupTitles.get(groupPosition) + " (" + count + ")");

        // spinner only on Group 2 while scanning
        if (groupPosition == 1 && isScanning) {
            headerProgress.setVisibility(View.VISIBLE);
        } else {
            headerProgress.setVisibility(View.GONE);
        }

        // Animate rotation: 0° (right) collapsed, 90° (down) expanded
        arrow.animate().rotation(isExpanded ? 90f : 0f).setDuration(150).start();

        return convertView;
    }

    // --- Child content for each group ---

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
                             View convertView, ViewGroup parent) {

        if (groupPosition == 0) {
            // GROUP 1: ListView of simple items
            View v = LayoutInflater.from(context).inflate(R.layout.group1_child, parent, false);
            final ListView listView = v.findViewById(R.id.group1ListView);
            final TextView emptyText = v.findViewById(R.id.group1EmptyText);
            listView.setEmptyView(emptyText);

            ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                    context,
                    R.layout.item_group1_list,
                    R.id.itemGroup1Text,
                    group1Data
            ) {
                @Override
                public View getView(final int position, View convertView, ViewGroup parent) {
                    View row = convertView;
                    if (row == null) {
                        row = LayoutInflater.from(getContext()).inflate(R.layout.item_group1_list, parent, false);
                    }
                    TextView tv = row.findViewById(R.id.itemGroup1Text);
                    String itemText = getItem(position);
                    tv.setText(itemText);
                    return row;
                }
            };

            listView.setAdapter(adapter);

            // Auto-resize
            listView.post(() -> MainActivity.setListViewHeightBasedOnChildren(listView));

            // Handle on click events
            listView.setOnItemClickListener((parent1, view, position, id) -> {
                String item = group1Data.get(position);
                String[] splicedText = item.split("\n");
                String deviceName = splicedText[0];
                String macAddress = splicedText[1];

                // Create Intent to start TerminalActivity
                Intent intent = new Intent(mainActivity, TerminalActivity.class);
                intent.putExtra("DEVICE_NAME", deviceName);
                intent.putExtra("DEVICE_ADDRESS", macAddress);
                mainActivity.startActivity(intent);
            });

            return v;

        } else {
            // GROUP 2: Button + ListView with connect MaterialButtons
            View v = LayoutInflater.from(context).inflate(R.layout.group2_child, parent, false);

            // Top button in Group 2 layout (still a normal Button in child_group2.xml unless you changed it)
            MaterialButton mainButton = v.findViewById(R.id.group2Button);
            final ListView innerList = v.findViewById(R.id.group2ListView);
            TextView emptyText = v.findViewById(R.id.group2EmptyText);
            TextView foundNote = v.findViewById(R.id.foundNote);          // italic top-right
            innerList.setEmptyView(emptyText);

            // Control progress + found note
            if (!isScanning && foundCount > 0) {
                foundNote.setVisibility(View.VISIBLE);
                foundNote.setText("Found " + foundCount + " devices");
            } else {
                foundNote.setVisibility(View.GONE);
            }

            // Inline adapter that inflates item_group2_list (with MaterialButton)
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                    context,
                    R.layout.item_group2_list,
                    R.id.group2ItemName,
                    group2Data
            ) {
                @Override
                public View getView(final int position, View convertView, ViewGroup parent) {
                    View row = convertView;
                    if (row == null) {
                        row = LayoutInflater.from(getContext()).inflate(R.layout.item_group2_list, parent, false);
                    }
                    TextView name = row.findViewById(R.id.group2ItemName);
                    MaterialButton connectBtn = row.findViewById(R.id.connectButton);

                    final String item = getItem(position);
                    name.setText(item);
                    String[] splicedText = item.split("\n");
                    String deviceName = splicedText[0];
                    String macAddress = splicedText[1];

                    connectBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            mainActivity.connectToDevice(mainActivity.MY_UUID, deviceName, macAddress);
                        }
                    });

                    row.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Toast.makeText(getContext(), deviceName + "\n" + macAddress, Toast.LENGTH_SHORT).show();
                        }
                    });

                    return row;
                }
            };

            innerList.setAdapter(adapter);

            // Ensure all Group 2 items are visible
            innerList.post(new Runnable() {
                @Override
                public void run() {
                    MainActivity.setListViewHeightBasedOnChildren(innerList);
                }
            });

            // Main action button click
            mainButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mainActivity.startScan(context, mainButton, innerList, adapter, group2Data);
                    mainActivity.setListViewHeightBasedOnChildren(innerList);
                }
            });

            // If currently scanning, make the button reflect it
            if (isScanning) {
                mainButton.setEnabled(false);
                mainButton.setText("Scanning...");
                // (simple visual cue; Activity animates it too)
            } else {
                mainButton.setEnabled(true);
                mainButton.setText("Scan Devices");
            }

            return v;
        }
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }
}
