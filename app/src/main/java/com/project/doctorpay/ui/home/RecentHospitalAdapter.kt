import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.project.doctorpay.R
import com.project.doctorpay.db.HospitalInfo

class RecentHospitalAdapter(
    private val onItemClick: (HospitalInfo) -> Unit
) : RecyclerView.Adapter<RecentHospitalAdapter.ViewHolder>() {

    private var hospitals: List<HospitalInfo> = emptyList()

    fun submitList(newList: List<HospitalInfo>) {
        hospitals = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent_hospital, parent, false)
        return ViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(hospitals[position])
    }

    override fun getItemCount(): Int = hospitals.size

    class ViewHolder(
        view: View,
        private val onItemClick: (HospitalInfo) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val nameTextView: TextView = view.findViewById(R.id.tvHospitalName)
        private val typeTextView: TextView = view.findViewById(R.id.tvHospitalType)

        fun bind(hospital: HospitalInfo) {
            nameTextView.text = hospital.name
            typeTextView.text = hospital.clCdNm
            itemView.setOnClickListener { onItemClick(hospital) }
        }
    }
}