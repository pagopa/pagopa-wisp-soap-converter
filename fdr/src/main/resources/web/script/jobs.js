//import React from "./libs/react.js";
//import htm from "./libs/htm.module.js";
//const html = htm.bind(React.createElement);

//import ActionButton from './actionButton.js'

function usePollingEffect(
    asyncCallback,
    dependencies = [],
    { 
      interval = 10_000, // 10 seconds,
      onCleanUp = () => {}
    } = {},
  ) {
    const timeoutIdRef = React.useRef(null)
    React.useEffect(() => {
      let _stopped = false
      // Side note: preceding semicolon needed for IIFEs.
      ;(async function pollingCallback() {
        try {
          await asyncCallback()
        } finally {
          // Set timeout after it finished, unless stopped
          timeoutIdRef.current = !_stopped && setTimeout(
            pollingCallback,
            interval
          )
        }
      })()
      // Clean up if dependencies change
      return () => {
        _stopped = true // prevent racing conditions
        clearTimeout(timeoutIdRef.current)
        onCleanUp()
      }
    }, [...dependencies, interval])
  }

function formatDuration(timeDiff){

    var hours = Math.floor(timeDiff / (3600 * 1000))
    var timeDiff = timeDiff - (hours * 3600 * 1000) 
    var minutes = Math.floor(timeDiff / (60 * 1000))
    var timeDiff = timeDiff - (minutes * 60 * 1000) 
    var seconds = Math.floor(timeDiff / (1000))

    return `${hours>0?`${hours}h:`:''}${minutes>0?`${minutes}m:`:''}${seconds>=0?`${seconds<10?`0${seconds}`:seconds}s`:''}`
}

function Jobs(props) {

    const [job, setJob] = React.useState(
      JSON.parse(localStorage.getItem('jobFilter')) || "all"
    );
    const [jobResetted, setJobResetted] = React.useState(0);

    const [timeFilter, setTimeFilter] = React.useState(
      JSON.parse(localStorage.getItem('timeFilter')) || "all"
    );
    const [onlyrunning, setOnlyrunning] = React.useState(
      JSON.parse(localStorage.getItem('onlyrunning')) || false
    );
    const [jobs, setJobs] = React.useState([]);
    const [serverDate, setServerDate] = React.useState([]);
    const [refreshRate, setRefreshRate] = React.useState(1000);
    const [firstLoaded, setFirstLoaded] = React.useState(false);

    React.useEffect(() => {
      localStorage.setItem("jobFilter", JSON.stringify(job));
    }, [job]);

    React.useEffect(() => {
      localStorage.setItem("timeFilter", JSON.stringify(timeFilter));
    }, [timeFilter]);

    React.useEffect(() => {
      localStorage.setItem("onlyrunning", JSON.stringify(onlyrunning));
    }, [onlyrunning]);

    usePollingEffect(
        async () => {
            if(!firstLoaded)props.setLoading(true)
            const cl = await fetch(`${baseUrl}/jobsStatus?timeFilter=${timeFilter}`)
            .then(data=>data.json())
            setJobs(cl.jobs)
            setServerDate(new Date(cl.date).getTime())
            props.setLoading(false)
            setFirstLoaded(true)
        },
        [timeFilter,firstLoaded,jobResetted],
        { interval: refreshRate}
    )
    return html`
    <div className="flex monospace column centeredV" >
        
        <div className="tabs">
          <span title="All" onClick=${()=>setJob("all")} className="${job=="all"?'selected':''}">
              <b>All</b><br/>
              <b className="high">
              ${jobs.map(s=>s.jobs.filter(s=>s.status == 'RUNNING').length).reduce((x,y)=>{
                return x+y
              },0)}
              </b> running
          </span>
          ${jobs.map(j=>{
              return html`<span title="${j.name}" key=${j.jobName} onClick=${()=>setJob(j.jobName)} className="${job==j.jobName?'selected':''}">
              <b>${j.name}</b><br/>
              <b className="high">${j.jobs.filter(d=>d.status === 'RUNNING').length}</b> running
              </span>`
          })}
      </div>
      <hr/>
      <div className="jobFilters">
            <label htmlFor="jobTime">Mostra solo iniziati da</label>
            <select id="jobTime" value=${timeFilter} onChange=${(e)=>setTimeFilter(e.target.value)}>
                <option value="all">Sempre</option>
                <option value="1week">Una settimana</option>
                <option value="1day">Un giorno</option>
                <option value="1hour">Un'ora</option>
            </select>

            <label htmlFor="onlyrunning">Solo RUNNING</label>
            <input type="checkbox" id="onlyrunning" checked=${onlyrunning} onChange=${(e)=>setOnlyrunning(e.target.checked)}/>
        </div>
      <div className="">
        <div className="grid5 tabled" >
            <span className="alignleft header">Key</span>
            <span className="alignleft header">Status</span>
            <span className="alignleft header">Start</span>
            <span className="alignleft header">End</span>
            <span className="header" >Action</span>
            ${job == 'all' && jobs.map(j=>{ return html`<${React.Fragment} key=${j.jobName}>
            <span className="jobheader jobs${j.jobs.filter(j=>!onlyrunning || j.status == 'RUNNING').length}">${j.name} (${j.jobs.filter(j=>j.status == 'RUNNING').length} running)</span>
            ${j.jobs.filter(j=>!onlyrunning || j.status == 'RUNNING').map(j=>{
              var timediff = serverDate - new Date(j.start).getTime()
              var warning = (j.status === 'RUNNING' && timediff > 600000 && 'warning')
              var isnew = (j.status === 'RUNNING' && timediff < 10000 && 'highlight')
              return html`<${React.Fragment} key=${j.id}}>
              <span className="alignleft ${warning} ${isnew}">
                <i className="ellipsis" title=${j.key}>${j.key}</i>
              </span>
              <span className="alignleft ${j.status} ${warning} ${isnew}">
                <i className="ellipsis">${j.status}</i>
                </span>
              <span className="alignleft ${warning} ${isnew}">
              <i className="ellipsis">${j.start}</i>
              </span>
              <span className="alignleft ${warning} ${isnew}">
              <i className="ellipsis">${j.status === 'RUNNING'?formatDuration(serverDate - new Date(j.start).getTime()):j.end}</i>
              </span>
              <span className="${warning} ${isnew}">
                ${j.status=='RUNNING'?html`<${ActionButton} small=${true} url="${baseUrl}/jobs/reset?id=${j.id}" text="Reset" callback=${()=>setJobResetted(j.id)}/>`:''}
              </span>
              </${React.Fragment}>
              `
            })}
            </${React.Fragment}>
            `
          }) }
            ${job != 'all' && jobs.find(x=>x.jobName==job) && jobs.find(x=>x.jobName==job).jobs.filter(j=>!onlyrunning || j.status == 'RUNNING').map(j=>{
              var timediff = serverDate - new Date(j.start).getTime()
              var warning = (j.status === 'RUNNING' && timediff > 600000 && 'warning')
              var isnew = (j.status === 'RUNNING' && timediff < 10000 && 'highlight')
              return html`<${React.Fragment} key=${j.id}>
              <span className="alignleft ${warning} ${isnew}">
                <i className="ellipsis" title=${j.key}>${j.key}</i>
              </span>
              <span className="alignleft ${j.status} ${warning} ${isnew}">
                <i className="ellipsis">${j.status}</i>
                </span>
              <span className="alignleft ${warning} ${isnew}">
              <i className="ellipsis">${j.start}</i>
              </span>
              <span className="alignleft ${warning} ${isnew}">
              <i className="ellipsis">${j.status === 'RUNNING'?formatDuration(serverDate - new Date(j.start).getTime()):j.end}</i>
              </span>
              <span className="${warning} ${isnew}">
                ${j.status=='RUNNING'?html`<${ActionButton} small=${true} url="${baseUrl}/jobs/reset?id=${j.id}" text="Reset" callback=${()=>setJobResetted(j.id)}/>`:''}
              </span>
              </${React.Fragment}>
              `
            })}
        </div>
      </div>
        <div className="refreshBox flex">Refresh Rate
            <select id="refreshRate" value=${refreshRate} onChange=${(e)=>setRefreshRate(e.target.value)}>
                <option value="1000">1 secondo</option>
                <option value="5000">5 secondi</option>
                <option value="10000">10 secondi</option>
                <option value="30000">30 secondi</option>
                <option value="60000">1 minuto</option>
            </select>
        </div>
    </div>
    `;
  }

//export default Jobs