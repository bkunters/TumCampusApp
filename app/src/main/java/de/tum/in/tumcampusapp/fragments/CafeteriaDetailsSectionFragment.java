package de.tum.in.tumcampusapp.fragments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.text.SpannableString;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import de.tum.in.tumcampusapp.R;
import de.tum.in.tumcampusapp.auxiliary.CafeteriaPrices;
import de.tum.in.tumcampusapp.auxiliary.Const;
import de.tum.in.tumcampusapp.auxiliary.Utils;
import de.tum.in.tumcampusapp.cards.CafeteriaMenuCard;
import de.tum.in.tumcampusapp.managers.CafeteriaMenuManager;
import de.tum.in.tumcampusapp.managers.OpenHoursManager;

/**
 * Fragment for each cafeteria-page.
 */
public class CafeteriaDetailsSectionFragment extends Fragment {
    private static final Pattern SPLIT_ANNOTATIONS_PATTERN = Pattern.compile("\\(([A-Za-z0-9]+),");
    private static final Pattern NUMERICAL_ANNOTATIONS_PATTERN = Pattern.compile("\\(([1-9]|10|11)\\)");

    /**
     * Inflates the cafeteria menu layout.
     * This is put into an extra static method to be able to
     * reuse it in {@link CafeteriaMenuCard}
     *
     * @param rootView    Parent layout
     * @param cafeteriaId Cafeteria id
     * @param dateStr     Date in yyyy-mm-dd format
     * @param big         True to show big lines
     */
    @SuppressLint("ShowToast")
    public static List<View> showMenu(LinearLayout rootView, int cafeteriaId, String dateStr, boolean big) {
        // initialize a few often used things
        final Context context = rootView.getContext();
        final Map<String, String> rolePrices = CafeteriaPrices.getRolePrices(context);
        final int padding = (int) context.getResources().getDimension(R.dimen.card_text_padding);
        List<View> addedViews = new ArrayList<>(32);
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final CafeteriaMenuManager cmm = new CafeteriaMenuManager(context);

        // Get menu items
        Cursor cursorCafeteriaMenu = new CafeteriaMenuManager(context).getTypeNameFromDbCard(cafeteriaId, dateStr);

        TextView textview;
        if (!big) {
            // Show opening hours
            OpenHoursManager lm = new OpenHoursManager(context);
            textview = new TextView(context);
            textview.setText(lm.getHoursByIdAsString(context, cafeteriaId, Utils.getDate(dateStr)));
            textview.setTextColor(ContextCompat.getColor(context, R.color.sections_green));
            rootView.addView(textview);
            addedViews.add(textview);
        }

        // Show cafeteria menu
        String curShort = "";
        if (cursorCafeteriaMenu.moveToFirst()) {
            do {
                String typeShort = cursorCafeteriaMenu.getString(3);
                String typeLong = cursorCafeteriaMenu.getString(0);
                final String menu = cursorCafeteriaMenu.getString(1);

                // Skip unchecked categories if showing card
                boolean shouldShow = Utils.getSettingBool(context, "card_cafeteria_" + typeShort,
                        "tg".equals(typeShort) || "ae".equals(typeShort));
                if (!big && !shouldShow) {
                    continue;
                }

                // Add header if we start with a new category
                if (!typeShort.equals(curShort)) {
                    curShort = typeShort;
                    View view = inflater.inflate(big ? R.layout.list_header_big : R.layout.card_list_header, rootView, false);
                    textview = (TextView) view.findViewById(R.id.list_header);
                    textview.setText(typeLong.replaceAll("[0-9]", "").trim());
                    rootView.addView(view);
                    addedViews.add(view);
                }

                // Show menu item

                final SpannableString text = menuToSpan(context, big ? menu : prepare(menu));
                if (rolePrices.containsKey(typeLong)) {
                    // If price is available
                    View view = inflater.inflate(big ? R.layout.price_line_big : R.layout.card_price_line, rootView, false);
                    textview = (TextView) view.findViewById(R.id.line_name);
                    TextView priceView = (TextView) view.findViewById(R.id.line_price);
                    final ToggleButton favDish = (ToggleButton) view.findViewById(R.id.favortieDish);
                    favDish.setTag(menu + "__" + cafeteriaId);
                    /**
                     * saved dish id in the favoriteDishButton tag.
                     * onButton checked getTag->DishID and mark it as favorite locally (favorite=1)
                     */
                    textview.setText(text);
                    priceView.setText(String.format("%s €", rolePrices.get(typeLong)));
                    rootView.addView(view);
                    addedViews.add(view);

                    Cursor c = cmm.checkIfFavoriteDish(favDish.getTag().toString());
                    if (c.getCount() > 0) {
                        if (!favDish.isChecked()) {
                            favDish.setChecked(true);
                        }
                    } else {
                        if (favDish.isChecked()) {
                            favDish.setChecked(false);
                        }
                    }

                    View.OnClickListener favoriteToggleButtonListener = new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            String id = view.getTag().toString();
                            String[] data = id.split("__");
                            String dishname = data[0];
                            int mensaId = Integer.parseInt(data[1]);

                            /*AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                            Intent myIntent = new Intent(context, FavoriteDishAlarm.class);*/

                            if (((ToggleButton) view).isChecked()) {
                                DateTimeFormatter formatter = DateTimeFormat.forPattern("dd-MM-yyyy");
                                String currentDate = DateTime.now().toString(formatter);
                                cmm.insertFavoriteDish(mensaId, dishname, currentDate, favDish.getTag().toString());
                                cmm.notifyFavoriteFoodService();
                                /*Cursor c = cmm.getFavoriteDishNextDates(mensaId, dishname);
                                if (c.getCount() > 0) {
                                    while (c.moveToNext()) {
                                        cmm.insertFavoriteDish(mensaId, dishname, c.getString(0), favDish.getTag().toString());
                                        Cursor cur = cmm.getLastInsertedDishId(mensaId, dishname);
                                        DateTime dt = formatter.parseDateTime(c.getString(0)).withHourOfDay(9);
                                        long millsToAlarm = dt.getMillis() - DateTime.now().getMillis();

                                        int alertID = 0;
                                        if (cur.moveToFirst()) {
                                            alertID = cur.getInt(0);
                                        }

                                        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, alertID, myIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                                        alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + millsToAlarm, pendingIntent);
                                    }
                                }*/
                            } else {
                                /*Cursor curs = cmm.getFavoriteDishAllIds(mensaId, dishname);
                                while (curs.moveToNext()) {
                                    int alertId = curs.getInt(0);
                                    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, alertId, myIntent, PendingIntent.FLAG_CANCEL_CURRENT);
                                    pendingIntent.cancel();
                                    alarmManager.cancel(pendingIntent);
                                }*/
                                cmm.deleteFavoriteDish(mensaId, dishname);
                            }
                        }
                    };

                    favDish.setOnClickListener(favoriteToggleButtonListener);

                } else {
                    // Without price
                    textview = new TextView(context);
                    textview.setText(text);
                    textview.setPadding(padding, padding, padding, padding);
                    rootView.addView(textview);
                    addedViews.add(textview);
                }
            } while (cursorCafeteriaMenu.moveToNext());
        }
        cursorCafeteriaMenu.close();
        return addedViews;
    }

    /**
     * Converts menu text to {@link SpannableString}.
     * Replaces all (v), ... annotations with images
     *
     * @param context Context
     * @param menu    Text with annotations
     * @return Spannable text with images
     */
    public static SpannableString menuToSpan(Context context, String menu) {
        final String processedMenu = splitAnnotations(menu);
        final SpannableString text = new SpannableString(processedMenu);
        replaceWithImg(context, processedMenu, text, "(v)", R.drawable.meal_vegan);
        replaceWithImg(context, processedMenu, text, "(f)", R.drawable.meal_veggie);
        replaceWithImg(context, processedMenu, text, "(R)", R.drawable.meal_beef);
        replaceWithImg(context, processedMenu, text, "(S)", R.drawable.meal_pork);
        replaceWithImg(context, processedMenu, text, "(GQB)", R.drawable.ic_gqb);
        replaceWithImg(context, processedMenu, text, "(99)", R.drawable.meal_alcohol);
        return text;
    }

    private static void replaceWithImg(Context context, String menu, SpannableString text, String sym, int drawable) {
        int ind = menu.indexOf(sym);
        while (ind >= 0) {
            ImageSpan is = new ImageSpan(context, drawable);
            text.setSpan(is, ind, ind + sym.length(), 0);
            ind = menu.indexOf(sym, ind + sym.length());
        }
    }

    /**
     * Replaces all annotations that cannot be replaces with images such as (1), ...
     *
     * @param menu Text to delete annotations from
     * @return Text without un-replaceable annotations
     */
    private static String prepare(String menu) {
        final String tmp = splitAnnotations(menu);
        return NUMERICAL_ANNOTATIONS_PATTERN.matcher(tmp).replaceAll("");
    }

    @NonNull
    private static String splitAnnotations(String menu) {
        int len;
        String tmp = menu;
        do {
            len = tmp.length();
            tmp = SPLIT_ANNOTATIONS_PATTERN.matcher(tmp).replaceFirst("($1)(");
        } while (tmp.length() > len);
        return tmp;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_cafeteriadetails_section, container, false);
        LinearLayout root = (LinearLayout) rootView.findViewById(R.id.layout);
        int cafeteriaId = getArguments().getInt(Const.CAFETERIA_ID);
        String date = getArguments().getString(Const.DATE);
        showMenu(root, cafeteriaId, date, true);
        return rootView;
    }
}
