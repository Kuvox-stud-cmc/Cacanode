import DateTimePicker,{type DateTimePickerEvent} from '@react-native-community/datetimepicker';
import { Platform,StyleSheet,View } from 'react-native';
import { AppText } from '@/components/ui/app-text';
import { spacing } from '@/constants/theme';
export function DateTimeField({label,value,onChange,minimumDate}:{label:string;value:Date;onChange:(value:Date)=>void;minimumDate?:Date}){const changed=(_event:DateTimePickerEvent,next?:Date)=>{if(next)onChange(next)};return <View style={styles.field}><AppText variant="bodySmall">{label}</AppText><DateTimePicker display={Platform.OS==='ios'?'compact':'default'} minimumDate={minimumDate} mode="datetime" onChange={changed} value={value}/></View>}
const styles=StyleSheet.create({field:{gap:spacing.xs}});
