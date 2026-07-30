import { Pressable,StyleSheet,View } from 'react-native';
import { AppText } from '@/components/ui/app-text';
import { radii,spacing } from '@/constants/theme';
import { useAppTheme } from '@/hooks/use-app-theme';

export type Choice={label:string;value:string};
export function ChoiceField({label,value,choices,onChange,disabled=false}:{label:string;value:string|null;choices:Choice[];onChange:(value:string)=>void;disabled?:boolean}){
  const theme=useAppTheme();
  return <View style={styles.field}><AppText variant="bodySmall" style={styles.label}>{label}</AppText><View style={styles.choices}>{choices.map(choice=>{const selected=value===choice.value;return <Pressable accessibilityRole="radio" accessibilityState={{checked:selected,disabled}} disabled={disabled} key={choice.value} onPress={()=>onChange(choice.value)} style={[styles.choice,{borderColor:selected?theme.colors.primary:theme.colors.border,backgroundColor:selected?theme.colors.primarySoft:theme.colors.surface},disabled&&styles.disabled]}><AppText variant="bodySmall" style={selected?{color:theme.colors.primaryText}:undefined}>{choice.label}</AppText></Pressable>})}</View></View>;
}
const styles=StyleSheet.create({field:{gap:spacing.sm},label:{fontWeight:'600'},choices:{flexDirection:'row',flexWrap:'wrap',gap:spacing.sm},choice:{borderRadius:radii.md,borderWidth:1,minHeight:42,justifyContent:'center',paddingHorizontal:spacing.md,paddingVertical:spacing.sm},disabled:{opacity:.55}});
